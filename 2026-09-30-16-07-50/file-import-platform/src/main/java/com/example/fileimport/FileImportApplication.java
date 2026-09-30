package com.example.fileimport;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * 通用文件采集入库调度平台 —— 启动类。
 *
 * <h3>项目定位</h3>
 * 一套"配置驱动"的文件采集框架：用同一个流程骨架支撑 N 个定时任务，
 * 新增任务只需 <b>加一段 yml 配置 + 写一个 Handler 类</b>，无需改动框架代码。
 *
 * <h3>快速开始</h3>
 * <ol>
 *   <li>执行 {@code src/main/resources/db/schema.sql} 建表</li>
 *   <li>修改 {@code application.yml} 中的数据库连接与文件目录</li>
 *   <li>{@code mvn spring-boot:run} 启动（默认本地调度模式，无需 ZooKeeper）</li>
 *   <li>往 {@code data/in/order/} 投放 CSV 文件，观察日志与数据库</li>
 *   <li>新增任务请阅读 {@code README.md} 的"如何新增一个任务"章节</li>
 * </ol>
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.example.fileimport.**.mapper")
public class FileImportApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(FileImportApplication.class, args);

        Environment env = context.getEnvironment();
        String port = env.getProperty("server.port", "8080");
        String scheduleMode = env.getProperty("app.schedule.mode", "local");

        log.info("""
                
                ============================================================
                  文件采集入库平台启动成功
                ------------------------------------------------------------
                  调度模式   : {}
                  服务端口   : {}
                  健康检查   : http://localhost:{}/actuator/health
                  指标监控   : http://localhost:{}/actuator/prometheus
                  任务列表   : http://localhost:{}/ops/tasks
                  手动触发   : POST http://localhost:{}/ops/tasks/{{taskCode}}/trigger
                ============================================================
                """, scheduleMode, port, port, port, port, port);
    }
}
