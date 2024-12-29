package com.simple.jupiter.registry;

import com.simple.jupiter.register.RegisterMeta;
import com.simple.jupiter.concurrent.collection.ConcurrentSet;
import com.simple.jupiter.util.Lists;
import com.simple.jupiter.util.Maps;
import com.simple.jupiter.util.Requires;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

public class RegisterInfoContext {

    // 指定服务都有哪些节点注册
    private final ConcurrentMap<RegisterMeta.ServiceMeta, ConfigWithVersion<ConcurrentMap<RegisterMeta.Address, RegisterMeta>>>
            globalRegisterInfoMap = Maps.newConcurrentMap();
    // 指定节点都注册了哪些服务
    private final ConcurrentMap<RegisterMeta.Address, ConcurrentSet<RegisterMeta.ServiceMeta>>
            globalServiceMetaMap = Maps.newConcurrentMap();

    public ConfigWithVersion<ConcurrentMap<RegisterMeta.Address, RegisterMeta>> getRegisterMeta
            (RegisterMeta.ServiceMeta serviceMeta) {

        ConfigWithVersion<ConcurrentMap<RegisterMeta.Address, RegisterMeta>> config =
                globalRegisterInfoMap.get(serviceMeta);
        if (config == null) {
            ConfigWithVersion<ConcurrentMap<RegisterMeta.Address, RegisterMeta>> newConfig =
                    ConfigWithVersion.newInstance();
            newConfig.setConfig(Maps.newConcurrentMap());
            config = globalRegisterInfoMap.putIfAbsent(serviceMeta, newConfig);
            if (config == null) {
                config = newConfig;
            }
        }
        return config;
    }

    public ConcurrentSet<RegisterMeta.ServiceMeta> getServiceMeta(RegisterMeta.Address address) {
        ConcurrentSet<RegisterMeta.ServiceMeta> serviceMetaSet = globalServiceMetaMap.get(address);
        if (serviceMetaSet == null) {
            ConcurrentSet<RegisterMeta.ServiceMeta> newServiceMetaSet = new ConcurrentSet<>();
            serviceMetaSet = globalServiceMetaMap.putIfAbsent(address, newServiceMetaSet);
            if (serviceMetaSet == null) {
                serviceMetaSet = newServiceMetaSet;
            }
        }
        return serviceMetaSet;
    }

    public Object publishLock(ConfigWithVersion<ConcurrentMap<RegisterMeta.Address, RegisterMeta>> config) {
        return Requires.requireNotNull(config, "publish lock");
    }

    // - Monitor -------------------------------------------------------------------------------------------------------

    public List<RegisterMeta.Address> listPublisherHosts() {
        return Lists.newArrayList(globalServiceMetaMap.keySet());
    }

    public List<RegisterMeta.Address> listAddressesByService(RegisterMeta.ServiceMeta serviceMeta) {
        return Lists.newArrayList(getRegisterMeta(serviceMeta).getConfig().keySet());
    }

    public List<RegisterMeta.ServiceMeta> listServicesByAddress(RegisterMeta.Address address) {
        return Lists.newArrayList(getServiceMeta(address));
    }
}


