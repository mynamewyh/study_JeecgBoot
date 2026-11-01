package org.jeecg.modules.demo.yingzhiren.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.aspect.annotation.AutoLog;
import org.jeecg.common.system.base.controller.JeecgController;
import org.jeecg.common.system.query.QueryGenerator;
import org.jeecg.modules.demo.yingzhiren.entity.YingZhiRenScore;
import org.jeecg.modules.demo.yingzhiren.service.IYingZhiRenScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Description: 影之刃零通关成绩
 * @Author: wangyuhang
 * @Date: 2026-11-01
 */
@Slf4j
@Tag(name = "影之刃零通关成绩")
@RestController
@RequestMapping("/demo/yingzhiren")
public class YingZhiRenScoreController extends JeecgController<YingZhiRenScore, IYingZhiRenScoreService> {

    @Autowired
    private IYingZhiRenScoreService yingZhiRenScoreService;

    /**
     * 分页列表查询（对应 StudyDay1 的 void2：按姓名/BOSS 查询历史）
     */
    @Operation(summary = "分页列表查询")
    @GetMapping(value = "/list")
    public Result<?> list(YingZhiRenScore yingZhiRenScore,
                          @RequestParam(name = "pageNo", defaultValue = "1") Integer pageNo,
                          @RequestParam(name = "pageSize", defaultValue = "10") Integer pageSize,
                          HttpServletRequest req) {
        QueryWrapper<YingZhiRenScore> queryWrapper = QueryGenerator.initQueryWrapper(yingZhiRenScore, req.getParameterMap());
        queryWrapper.orderByDesc("create_time");
        Page<YingZhiRenScore> page = new Page<YingZhiRenScore>(pageNo, pageSize);
        IPage<YingZhiRenScore> pageList = yingZhiRenScoreService.page(page, queryWrapper);
        return Result.OK(pageList);
    }

    /**
     * 新增
     */
    @PostMapping(value = "/add")
    @AutoLog(value = "影之刃零成绩-新增")
    @Operation(summary = "新增")
    public Result<?> add(@RequestBody YingZhiRenScore yingZhiRenScore) {
        yingZhiRenScoreService.save(yingZhiRenScore);
        return Result.OK("添加成功！");
    }

    /**
     * 编辑
     */
    @AutoLog(value = "影之刃零成绩-编辑")
    @Operation(summary = "编辑")
    @RequestMapping(value = "/edit", method = {RequestMethod.PUT, RequestMethod.POST})
    public Result<?> edit(@RequestBody YingZhiRenScore yingZhiRenScore) {
        yingZhiRenScoreService.updateById(yingZhiRenScore);
        return Result.OK("更新成功！");
    }

    /**
     * 通过 id 删除
     */
    @AutoLog(value = "影之刃零成绩-删除")
    @DeleteMapping(value = "/delete")
    @Operation(summary = "通过ID删除")
    public Result<?> delete(@RequestParam(name = "id", required = true) String id) {
        yingZhiRenScoreService.removeById(id);
        return Result.OK("删除成功!");
    }

    /**
     * 批量删除
     */
    @DeleteMapping(value = "/deleteBatch")
    @Operation(summary = "批量删除")
    public Result<?> deleteBatch(@RequestParam(name = "ids", required = true) String ids) {
        this.yingZhiRenScoreService.removeByIds(Arrays.asList(ids.split(",")));
        return Result.OK("批量删除成功！");
    }

    /**
     * 通过 id 查询
     */
    @GetMapping(value = "/queryById")
    @Operation(summary = "通过ID查询")
    public Result<?> queryById(@RequestParam(name = "id", required = true) String id) {
        return Result.OK(yingZhiRenScoreService.getById(id));
    }

    /**
     * 导出 excel
     */
    @RequestMapping(value = "/exportXls")
    @Operation(summary = "导出Excel")
    public ModelAndView exportXls(HttpServletRequest request, YingZhiRenScore yingZhiRenScore) {
        return super.exportXls(request, yingZhiRenScore, YingZhiRenScore.class, "影之刃零通关成绩");
    }

    /**
     * 多人排名（对应 StudyDay1 的 void3）
     * 只统计通关成功(is_succeed=1)的记录，按玩家个人最快通关时长升序排名，
     * 并给出「较首次进步% / 较上次进步%」。
     */
    @Operation(summary = "多人排名")
    @GetMapping(value = "/rank")
    public Result<?> rank() {
        QueryWrapper<YingZhiRenScore> qw = new QueryWrapper<>();
        qw.eq("is_succeed", 1)
          .isNotNull("clear_time")
          .orderByAsc("player_name")
          .orderByAsc("create_time");
        List<YingZhiRenScore> all = yingZhiRenScoreService.list(qw);

        // 按玩家分组（LinkedHashMap 保序）
        Map<String, List<YingZhiRenScore>> byPlayer = new LinkedHashMap<>();
        for (YingZhiRenScore r : all) {
            byPlayer.computeIfAbsent(r.getPlayerName(), k -> new ArrayList<>()).add(r);
        }

        List<RankRow> rows = new ArrayList<>();
        for (Map.Entry<String, List<YingZhiRenScore>> e : byPlayer.entrySet()) {
            List<YingZhiRenScore> runs = e.getValue();
            YingZhiRenScore best = runs.stream()
                    .min(Comparator.comparingInt(YingZhiRenScore::getClearTime)).orElse(null);
            YingZhiRenScore first = runs.get(0);
            YingZhiRenScore last = runs.get(runs.size() - 1);
            YingZhiRenScore prev = runs.size() >= 2 ? runs.get(runs.size() - 2) : null;

            RankRow row = new RankRow();
            row.setPlayer(e.getKey());
            row.setCount(runs.size());
            row.setBestTime(best.getClearTime());
            row.setLatestTime(last.getClearTime());
            // 进步%：正数=更快；只有 1 条记录时为 null
            row.setVsFirst(runs.size() >= 2 ? round1(progress(first.getClearTime(), last.getClearTime())) : null);
            row.setVsPrev(prev != null ? round1(progress(prev.getClearTime(), last.getClearTime())) : null);
            rows.add(row);
        }
        rows.sort(Comparator.comparingInt(RankRow::getBestTime));
        return Result.OK(rows);
    }

    /** 进步百分比：(旧用时 - 新用时) / 旧用时，正数表示更快 */
    private double progress(int from, int to) {
        if (from <= 0) {
            return 0;
        }
        return (from - to) * 100.0 / from;
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    /** 排名结果行 */
    @Data
    public static class RankRow {
        private String player;
        private int count;
        private int bestTime;
        private int latestTime;
        private Double vsFirst;
        private Double vsPrev;
    }
}
