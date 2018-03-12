package bisq.core.btc.wallet;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import java.util.Collections;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;



import io.bisq.network.Socks5MultiDiscovery;

public class WalletNetworkConfigTest {
    private static final int MODE = 0;

    private WalletConfig delegate;

    @Before
    public void setUp() {
        delegate = mock(WalletConfig.class);
    }

    @Test
    public void testProposePeersWhenProxyPresentAndNoPeers() {
        WalletNetworkConfig config = new WalletNetworkConfig(delegate, mock(NetworkParameters.class), MODE,
                mock(Socks5Proxy.class));
        config.proposePeers(Collections.emptyList());

        verify(delegate, never()).setPeerNodes(any());
        verify(delegate).setDiscovery(any(Socks5MultiDiscovery.class));
    }

    @Test
    public void testProposePeersWhenProxyNotPresentAndNoPeers() {
        WalletNetworkConfig config = new WalletNetworkConfig(delegate, mock(NetworkParameters.class), MODE,
                null);
        config.proposePeers(Collections.emptyList());

        verify(delegate, never()).setDiscovery(any(Socks5MultiDiscovery.class));
        verify(delegate, never()).setPeerNodes(any());
    }

    @Test
    public void testProposePeersWhenPeersPresent() {
        WalletNetworkConfig config = new WalletNetworkConfig(delegate, mock(NetworkParameters.class), MODE,
                null);
        config.proposePeers(Collections.singletonList(mock(PeerAddress.class)));

        verify(delegate, never()).setDiscovery(any(Socks5MultiDiscovery.class));
        verify(delegate).setPeerNodes(any());
    }
}
