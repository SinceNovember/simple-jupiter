package com.simple.jupiter.rpc.provider;

import com.simple.jupiter.rpc.model.metadata.ServiceWrapper;
import com.simple.jupiter.transport.Directory;

public interface LookupService {

    ServiceWrapper lookupService(Directory directory);

}
