package bisq.core.dao.node.messages;

import bisq.core.dao.blockchain.vo.BsqBlock;

import bisq.network.p2p.storage.messages.BroadcastMessage;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import lombok.EqualsAndHashCode;
import lombok.Getter;



import bisq.generated.protobuffer.PB;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class NewBsqBlockBroadcastMessage extends BroadcastMessage {
    private final BsqBlock bsqBlock;

    public NewBsqBlockBroadcastMessage(BsqBlock bsqBlock) {
        this(bsqBlock, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private NewBsqBlockBroadcastMessage(BsqBlock bsqBlock, int messageVersion) {
        super(messageVersion);
        this.bsqBlock = bsqBlock;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setNewBsqBlockBroadcastMessage(PB.NewBsqBlockBroadcastMessage.newBuilder()
                        .setBsqBlock(bsqBlock.toProtoMessage()))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.NewBsqBlockBroadcastMessage proto, int messageVersion) {
        return new NewBsqBlockBroadcastMessage(BsqBlock.fromProto(proto.getBsqBlock()),
                messageVersion);
    }
}
