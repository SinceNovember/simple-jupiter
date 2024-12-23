package com.simple.jupiter.transport.netty.estimator;

import com.simple.jupiter.transport.payload.PayloadHolder;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.FileRegion;
import io.netty.channel.MessageSizeEstimator;

/**
 * 消息size计算, 努力反应真实的IO水位线, 用于在发送到Channel时预估消息需要多大的内存，更好的管理内存
 */
public class JMessageSizeEstimator implements MessageSizeEstimator {

    private static final class HandleImpl implements Handle {

        private final int unknownSize;

        private HandleImpl(int unknownSize) {
            this.unknownSize = unknownSize;
        }

        @Override
        public int size(Object msg) {
            if (msg instanceof ByteBuf) {
                return ((ByteBuf) msg).readableBytes();
            }

            if (msg instanceof ByteBufHolder) {
                return ((ByteBufHolder) msg).content().readableBytes();
            }

            if (msg instanceof FileRegion) {
                return 0;
            }

            // jupiter object
            if (msg instanceof PayloadHolder) {
                return ((PayloadHolder) msg).size();
            }

            return unknownSize;
        }
    }

    public static final MessageSizeEstimator DEFAULT = new JMessageSizeEstimator(8);

    private final Handle handle;

    public JMessageSizeEstimator(int unknownSize) {
        if (unknownSize < 0) {
            throw new IllegalArgumentException("unknownSize: " + unknownSize + " (expected: >= 0)");
        }
        handle = new HandleImpl(unknownSize);
    }

    @Override
    public Handle newHandle() {
        return handle;
    }
}
