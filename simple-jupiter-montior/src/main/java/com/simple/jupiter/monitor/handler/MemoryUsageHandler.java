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
package com.simple.jupiter.monitor.handler;

import java.util.List;
import com.simple.jupiter.monitor.Command;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.JvmTools;
import com.simple.jupiter.util.StackTraceUtil;
import io.netty.channel.Channel;


/**
 * Jupiter
 * org.jupiter.monitor.handler
 *
 * @author jiachun.fjc
 */
public class MemoryUsageHandler implements CommandHandler {

    @Override
    public void handle(Channel channel, Command command, String... args) {
        try {
            List<String> memoryUsageList = JvmTools.memoryUsage();
            for (String usage : memoryUsageList) {
                channel.writeAndFlush(usage);
            }
            channel.writeAndFlush(JConstants.NEWLINE);
        } catch (Exception e) {
            channel.writeAndFlush(StackTraceUtil.stackTrace(e));
        }
    }
}
