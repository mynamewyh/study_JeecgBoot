package org.jeecg.modules.demo.hello;

import org.jeecg.common.api.vo.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.jeecg.config.shiro.IgnoreAuth;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HelloDemoController {
    @RestController
    @RequestMapping("/demo/hello")
    public static class HelloController {
        @IgnoreAuth
        @GetMapping("/say")
        public Result<String> say(@RequestParam(defaultValue = "php") String name) {
            List<String> list = Arrays.asList("a", "b");
            String joined = list.stream().map(String::toUpperCase).collect(Collectors.joining(","));
            return Result.OK("hello " + name + " " + joined);
        }
    }
}
