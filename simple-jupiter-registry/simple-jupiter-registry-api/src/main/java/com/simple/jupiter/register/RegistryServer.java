package com.simple.jupiter.register;

import com.simple.jupiter.util.Lists;
import com.simple.jupiter.util.Reflects;
import com.simple.jupiter.util.SystemPropertyUtil;
import com.simple.jupiter.util.ThrowUtil;

import java.lang.reflect.Constructor;
import java.net.SocketAddress;
import java.util.List;

public interface RegistryServer extends RegistryMonitor{

    void startRegistryServer();

    /**
     * 用于创建默认的注册中心实现(jupiter-registry-default), 当不使用jupiter-registry-default时, 不能有显示依赖.
     */
    class Default {
        private static final Class<RegistryServer> defaultRegistryClass;
        private static final List<Class<?>[]> allConstructorsParameterTypes;

        static {
            Class<RegistryServer> cls;
            try {
                cls = (Class<RegistryServer>) Class.forName(
                        SystemPropertyUtil.get("jupiter.registry.default", "org.jupiter.registry.DefaultRegistryServer"));
            } catch (ClassNotFoundException e) {
                cls = null;
            }
            defaultRegistryClass = cls;

            if (defaultRegistryClass != null) {
                allConstructorsParameterTypes = Lists.newArrayList();
                Constructor<?>[] array = defaultRegistryClass.getDeclaredConstructors();
                for (Constructor<?> c : array) {
                    allConstructorsParameterTypes.add(c.getParameterTypes());
                }
            } else {
                allConstructorsParameterTypes = null;
            }
        }

        public static RegistryServer createRegistryServer(int port) {
            return newInstance(port);
        }

        public static RegistryServer createRegistryServer(SocketAddress address) {
            return newInstance(address);
        }

        public static RegistryServer createRegistryServer(int port, int nWorkers) {
            return newInstance(port, nWorkers);
        }

        public static RegistryServer createRegistryServer(SocketAddress address, int nWorkers) {
            return newInstance(address, nWorkers);
        }

        private static RegistryServer newInstance(Object... parameters) {
            if (defaultRegistryClass == null || allConstructorsParameterTypes == null) {
                throw new UnsupportedOperationException("Unsupported default registry");
            }

            // 根据JLS方法调用的静态分派规则查找最匹配的方法parameterTypes
            Class<?>[] parameterTypes = Reflects.findMatchingParameterTypes(allConstructorsParameterTypes, parameters);
            if (parameterTypes == null) {
                throw new IllegalArgumentException("Parameter types");
            }

            try {
                Constructor<RegistryServer> c = defaultRegistryClass.getConstructor(parameterTypes);
                c.setAccessible(true);
                return c.newInstance(parameters);
            } catch (Exception e) {
                ThrowUtil.throwException(e);
            }
            return null; // should never get here
        }

    }
}
