/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simple.jupiter.util;

import java.util.Formatter;

/**
 * Jupiter constants.
 *
 * jupiter
 * org.jupiter.common.util
 *
 * @author jiachun.fjc
 */
public final class JConstants {

    /** 换行符 */
    public static final String NEWLINE;
    static {
        String newLine;
        try {
            newLine = new Formatter().format("%n").toString();
        } catch (Exception e) {
            newLine = "\n";
        }
        NEWLINE = newLine;
    }

    /**
     * 可配置的 available processors. 默认值是 {@link Runtime#availableProcessors()}.
     * 可以通过设置 system property "jupiter.available_processors" 来覆盖默认值.
     */
    public static final int AVAILABLE_PROCESSORS =
            SystemPropertyUtil.getInt("jupiter.available_processors", Runtime.getRuntime().availableProcessors());

    /** 未知应用名称 */
    public static final String UNKNOWN_APP_NAME = "UNKNOWN";
    /** 服务默认组别 */
    public static final String DEFAULT_GROUP = "Jupiter";
    /** 服务默认版本号 */
    public static final String DEFAULT_VERSION = "1.0.0";
    /** 默认的调用超时时间为3秒 **/
    public static final long DEFAULT_TIMEOUT =
            SystemPropertyUtil.getInt("jupiter.rpc.invoke.timeout", 3 * 1000);
    /** Server链路read空闲检测, 默认60秒, 60秒没读到任何数据会强制关闭连接 */
    public static final int READER_IDLE_TIME_SECONDS =
            SystemPropertyUtil.getInt("jupiter.io.reader.idle.time.seconds", 60);
    /** Client链路write空闲检测, 默认30秒, 30秒没有向链路中写入任何数据时Client会主动向Server发送心跳数据包 */
    public static final int WRITER_IDLE_TIME_SECONDS =
            SystemPropertyUtil.getInt("jupiter.io.writer.idle.time.seconds", 30);
    /** The number of flushes after which an explicit flush will be done */
    public static final int EXPLICIT_FLUSH_AFTER_FLUSHES =
            SystemPropertyUtil.getInt("jupiter.io.explicit.flush.after.flushes", 1024);
    /** Whether use low copy strategy for serialization */
    public static final boolean CODEC_LOW_COPY =
            SystemPropertyUtil.getBoolean("jupiter.io.codec.low_copy", true);

    /** Load balancer 默认预热时间 **/
    public static final int DEFAULT_WARM_UP =
            SystemPropertyUtil.getInt("jupiter.rpc.load-balancer.warm-up", 10 * 60 * 1000);
    /** Load balancer 默认权重 **/
    public static final int DEFAULT_WEIGHT =
            SystemPropertyUtil.getInt("jupiter.rpc.load-balancer.default.weight", 50);
    /** Load balancer 最大权重 **/
    public static final int MAX_WEIGHT =
            SystemPropertyUtil.getInt("jupiter.rpc.load-balancer.max.weight", 100);

    /** Suggest that the count of connections **/
    public static final int SUGGESTED_CONNECTION_COUNT =
            SystemPropertyUtil.getInt("jupiter.rpc.suggest.connection.count", Math.min(AVAILABLE_PROCESSORS, 4));

    /** Metrics csv reporter */
    public static final boolean METRIC_CSV_REPORTER =
            SystemPropertyUtil.getBoolean("jupiter.metric.csv.reporter", false);
    /** Metrics csv reporter directory */
    public static final String METRIC_CSV_REPORTER_DIRECTORY =
            SystemPropertyUtil.get("jupiter.metric.csv.reporter.directory", SystemPropertyUtil.get("user.dir"));
    /** Metrics reporter period */
    public static final int METRIC_REPORT_PERIOD =
            SystemPropertyUtil.getInt("jupiter.metric.report.period", 15);

    private JConstants() {}
}
