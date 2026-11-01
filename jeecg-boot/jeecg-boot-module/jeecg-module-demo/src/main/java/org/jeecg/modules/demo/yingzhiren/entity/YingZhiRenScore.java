package org.jeecg.modules.demo.yingzhiren.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import org.jeecg.common.system.base.entity.JeecgEntity;
import org.jeecgframework.poi.excel.annotation.Excel;

/**
 * @Description: 影之刃零通关成绩
 * @Author: wangyuhang
 * @Date: 2026-11-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@Schema(description = "影之刃零通关成绩")
@TableName("yingzhiren_score")
public class YingZhiRenScore extends JeecgEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 玩家姓名 */
    @Excel(name = "玩家姓名", width = 15)
    @Schema(description = "玩家姓名")
    private java.lang.String playerName;

    /** 关卡/BOSS名 */
    @Excel(name = "关卡/BOSS", width = 20)
    @Schema(description = "关卡/BOSS名")
    private java.lang.String boss;

    /** 通关时长(秒) */
    @Excel(name = "通关时长(秒)", width = 15)
    @Schema(description = "通关时长(秒)")
    private java.lang.Integer clearTime;

    /** 难度 */
    @Excel(name = "难度", width = 12)
    @Schema(description = "难度")
    private java.lang.String difficulty;

    /** 死亡次数 */
    @Excel(name = "死亡次数", width = 12)
    @Schema(description = "死亡次数")
    private java.lang.Integer deaths;

    /** 是否通关 1=是 0=否 */
    @Excel(name = "是否通关", width = 12)
    @Schema(description = "是否通关 1=是 0=否")
    private java.lang.Integer isSucceed;
}
