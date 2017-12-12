package com.nubits.nubitsj.jni;

import com.matthewmitchell.nubitsj.core.*;
import com.nubits.nubitsj.core.Coin;
import com.nubits.nubitsj.core.Sha256Hash;
import com.nubits.nubitsj.protocols.channels.PaymentChannelCloseException;
import com.nubits.nubitsj.protocols.channels.ServerConnectionEventHandler;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;

/**
 * An event listener that relays events to a native C++ object. A pointer to that object is stored in
 * this class using JNI on the native side, thus several instances of this can point to different actual
 * native implementations.
 */
public class NativePaymentChannelServerConnectionEventHandler extends ServerConnectionEventHandler {
    public long ptr;

    @Override
    public native void channelOpen(Sha256Hash channelId);

    @Override
    public native ListenableFuture<ByteString> paymentIncrease(Coin by, Coin to, ByteString info);

    @Override
    public native void channelClosed(PaymentChannelCloseException.CloseReason reason);
}
