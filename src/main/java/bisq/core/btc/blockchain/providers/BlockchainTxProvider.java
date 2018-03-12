package bisq.core.btc.blockchain.providers;

import bisq.core.provider.HttpClientProvider;

import org.bitcoinj.core.Coin;

import java.io.IOException;



import io.bisq.network.http.HttpClient;

public abstract class BlockchainTxProvider extends HttpClientProvider {
    public BlockchainTxProvider(HttpClient httpClient, String baseUrl) {
        super(httpClient, baseUrl);
    }

    public abstract Coin getFee(String transactionId) throws IOException;
}
