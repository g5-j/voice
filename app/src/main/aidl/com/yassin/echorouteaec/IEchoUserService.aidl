package com.yassin.echorouteaec;
import com.yassin.echorouteaec.IEchoCallback;
interface IEchoUserService {
    void start(String targetPackage, boolean communicationMode, boolean aec, boolean ns, boolean agc, in int[] sources, int priority, IEchoCallback callback);
    void stop();
    boolean isRunning();
}
