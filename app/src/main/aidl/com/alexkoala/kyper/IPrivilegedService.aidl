package com.alexkoala.kyper;

import com.alexkoala.kyper.IPrivilegedLogCallback;

interface IPrivilegedService {
    void setLogCallback(IPrivilegedLogCallback callback);
    boolean setPackageNetworkingEnabled(int uid, boolean enabled);
}
