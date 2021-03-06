/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.proposal.generic;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.proposal.compensation.CompensationAmountException;
import bisq.core.dao.proposal.compensation.consensus.OpReturnData;
import bisq.core.provider.fee.FeeService;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;
import bisq.common.util.Utilities;

import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;

import java.security.PublicKey;

import java.util.Date;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class GenericProposalManager {
    private final P2PService p2PService;
    private final BsqWalletService bsqWalletService;
    private final BtcWalletService btcWalletService;
    private final PublicKey signaturePubKey;
    private final FeeService feeService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public GenericProposalManager(P2PService p2PService,
                                  BsqWalletService bsqWalletService,
                                  BtcWalletService btcWalletService,
                                  KeyRing keyRing,
                                  FeeService feeService) {
        this.p2PService = p2PService;
        this.bsqWalletService = bsqWalletService;
        this.btcWalletService = btcWalletService;
        this.feeService = feeService;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }

    public GenericProposalPayload getNewGenericProposalPayload(String name,
                                                               String title,
                                                               String description,
                                                               String link) {
        checkArgument(p2PService.getAddress() != null, "p2PService.getAddress() must not be null");
        return new GenericProposalPayload(
                UUID.randomUUID().toString(),
                name,
                title,
                description,
                link,
                p2PService.getAddress(),
                signaturePubKey,
                new Date()
        );
    }

    // TODO move code to consensus package
    public GenericProposal prepareGenericProposal(GenericProposalPayload genericProposalPayload)
            throws InsufficientMoneyException, TransactionVerificationException, WalletException, CompensationAmountException {

        GenericProposal compensationRequest = new GenericProposal(genericProposalPayload,
                feeService.getMakeProposalFee().getValue());
        final Transaction preparedBurnFeeTx = bsqWalletService.getPreparedBurnFeeTx(compensationRequest.getFeeAsCoin());
        //compensationRequest.setFeeTx(preparedBurnFeeTx);
        checkArgument(!preparedBurnFeeTx.getInputs().isEmpty(), "preparedTx inputs must not be empty");

        // We use the key of the first BSQ input for signing the data
        TransactionOutput connectedOutput = preparedBurnFeeTx.getInputs().get(0).getConnectedOutput();
        checkNotNull(connectedOutput, "connectedOutput must not be null");
        DeterministicKey bsqKeyPair = bsqWalletService.findKeyFromPubKeyHash(connectedOutput.getScriptPubKey().getPubKeyHash());
        checkNotNull(bsqKeyPair, "bsqKeyPair must not be null");

        // We get the JSON of the object excluding signature and feeTxId
        String payloadAsJson = StringUtils.deleteWhitespace(Utilities.objectToJson(genericProposalPayload));
        // Signs a text message using the standard Bitcoin messaging signing format and returns the signature as a base64
        // encoded string.
        String signature = bsqKeyPair.signMessage(payloadAsJson);
        genericProposalPayload.setSignature(signature);

        String dataAndSig = payloadAsJson + signature;
        byte[] opReturnData = OpReturnData.getBytes(dataAndSig);

        final Transaction txWithBtcFee = btcWalletService.completePreparedGenericProposalTx(
                preparedBurnFeeTx,
                opReturnData);
        compensationRequest.setTx(bsqWalletService.signTx(txWithBtcFee));
        return compensationRequest;
    }
}
