package bisq.core.proto.persistable;

import bisq.core.arbitration.DisputeList;
import bisq.core.btc.AddressEntryList;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.proposal.ProposalList;
import bisq.core.payment.PaymentAccountList;
import bisq.core.proto.CoreProtoResolver;
import bisq.core.trade.TradableList;
import bisq.core.user.PreferencesPayload;
import bisq.core.user.UserPayload;

import bisq.network.p2p.peers.peerexchange.PeerList;
import bisq.network.p2p.storage.PersistableNetworkPayloadCollection;
import bisq.network.p2p.storage.PersistedEntryMap;
import bisq.network.p2p.storage.SequenceNumberMap;

import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.NavigationPath;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.Storage;

import io.bisq.generated.protobuffer.PB;

import com.google.inject.Provider;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CorePersistenceProtoResolver extends CoreProtoResolver implements PersistenceProtoResolver {
    private final Provider<BtcWalletService> btcWalletService;
    private final NetworkProtoResolver networkProtoResolver;
    private final File storageDir;

    @Inject
    public CorePersistenceProtoResolver(Provider<BtcWalletService> btcWalletService,
                                        NetworkProtoResolver networkProtoResolver,
                                        @Named(Storage.STORAGE_DIR) File storageDir) {
        this.btcWalletService = btcWalletService;
        this.networkProtoResolver = networkProtoResolver;
        this.storageDir = storageDir;

    }

    @Override
    public PersistableEnvelope fromProto(PB.PersistableEnvelope proto) {
        if (proto != null) {
            switch (proto.getMessageCase()) {
                case SEQUENCE_NUMBER_MAP:
                    return SequenceNumberMap.fromProto(proto.getSequenceNumberMap());
                case PERSISTED_ENTRY_MAP:
                    return PersistedEntryMap.fromProto(proto.getPersistedEntryMap().getPersistedEntryMapMap(),
                            networkProtoResolver);
                case PEER_LIST:
                    return PeerList.fromProto(proto.getPeerList());
                case ADDRESS_ENTRY_LIST:
                    return AddressEntryList.fromProto(proto.getAddressEntryList());
                case TRADABLE_LIST:
                    return TradableList.fromProto(proto.getTradableList(),
                            this,
                            new Storage<>(storageDir, this),
                            btcWalletService.get());
                case TRADE_STATISTICS_LIST:
                    throw new ProtobufferException("TRADE_STATISTICS_LIST is not used anymore");
                case DISPUTE_LIST:
                    return DisputeList.fromProto(proto.getDisputeList(),
                            this,
                            new Storage<>(storageDir, this));
                case PREFERENCES_PAYLOAD:
                    return PreferencesPayload.fromProto(proto.getPreferencesPayload(), this);
                case USER_PAYLOAD:
                    return UserPayload.fromProto(proto.getUserPayload(), this);
                case NAVIGATION_PATH:
                    return NavigationPath.fromProto(proto.getNavigationPath());
                case PAYMENT_ACCOUNT_LIST:
                    return PaymentAccountList.fromProto(proto.getPaymentAccountList(), this);
                case BSQ_BLOCK_CHAIN:
                    return BsqBlockChain.fromProto(proto.getBsqBlockChain());
                case PERSISTABLE_NETWORK_PAYLOAD_LIST:
                    return PersistableNetworkPayloadCollection.fromProto(proto.getPersistableNetworkPayloadList(), this);
                case PROPOSAL_LIST:
                    return ProposalList.fromProto(proto.getProposalList());
                default:
                    throw new ProtobufferException("Unknown proto message case(PB.PersistableEnvelope). messageCase=" + proto.getMessageCase());
            }
        } else {
            log.error("PersistableEnvelope.fromProto: PB.PersistableEnvelope is null");
            throw new ProtobufferException("PB.PersistableEnvelope is null");
        }
    }
}
