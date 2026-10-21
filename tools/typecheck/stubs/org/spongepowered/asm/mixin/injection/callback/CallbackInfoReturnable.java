package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfoReturnable<R> extends CallbackInfo {
    public R getReturnValue() { return null; }
    public float getReturnValueF() { return 0f; }
    public double getReturnValueD() { return 0d; }
    public int getReturnValueI() { return 0; }
    public boolean getReturnValueZ() { return false; }
    public void setReturnValue(R value) { }
}
