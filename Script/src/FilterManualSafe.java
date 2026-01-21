import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;

import org.apache.log4j.Logger;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.EventValues;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Contract;

import com.util.DBUtil;
import com.util.DerivedWallet;
import com.util.WalletService;

import io.reactivex.disposables.Disposable;

public class FilterManualSafe {

    private static final String blockchainUrl = "https://bsc-mainnet.core.chainstack.com/221c720cb0711d887ff7c345637783fc";
    private static final String contractAddress = "0x55d398326f99059fF775485246999027B3197955";
    private static final Logger logger = Logger.getLogger(FilterManualSafe.class);

    public static void main(String[] args) {

        try {
            Web3j web3j = Web3j.build(new HttpService(blockchainUrl));
            logger.info("Blockchain listener started...");

            // Transfer event definition
            final Event TRANSFER_EVENT = new Event("Transfer",
                    Arrays.<TypeReference<?>>asList(
                            new TypeReference<Address>(true) {},   // from
                            new TypeReference<Address>(true) {},   // to
                            new TypeReference<Uint256>() {}        // value (non-indexed)
                    ));

            // Filter from latest block onwards
            EthFilter filter = new EthFilter(DefaultBlockParameterName.LATEST,
                                             DefaultBlockParameterName.LATEST,
                                             contractAddress);
            filter.addSingleTopic(EventEncoder.encode(TRANSFER_EVENT));

            Disposable subscription = web3j.ethLogFlowable(filter).subscribe(log -> {

                try {
                    EventValues eventValues = Contract.staticExtractEventParameters(TRANSFER_EVENT, log);
                    if (eventValues == null) {
                        logger.warn("EventValues null for tx: " + log.getTransactionHash());
                        return;
                    }

                    // Safety checks
                    if (eventValues.getIndexedValues().size() < 2 || eventValues.getNonIndexedValues().isEmpty()) {
                        logger.warn("Unexpected number of values in tx: " + log.getTransactionHash());
                        return;
                    }

                    String from = ((Address) eventValues.getIndexedValues().get(0)).getValue();
                    String to = ((Address) eventValues.getIndexedValues().get(1)).getValue();
                    BigInteger value = ((Uint256) eventValues.getNonIndexedValues().get(0)).getValue();
                    BigDecimal amount = new BigDecimal(value).divide(BigDecimal.TEN.pow(18));

                    //logger.info("Transfer detected - tx: " + log.getTransactionHash() + ", from: " + from + ", to: " + to + ", amount: " + amount);

                    processDeposit(to, from, amount, log.getTransactionHash());

                } catch (Exception e) {
                    logger.error("Error processing log: " + log.getTransactionHash(), e);
                }

            }, error -> {
                logger.error("Subscription error: ", error);
            });

            // Keep the app running
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutting down listener...");
                subscription.dispose();
            }));

        } catch (Exception e) {
            logger.error("Failed to start listener: ", e);
        }
    }

    private static void processDeposit(String to, String from, BigDecimal amount, String txHash) {
        Connection con = null;
        try {
            // Get DB connection
            con = DBUtil.getConnection();
            if (con == null) {
                logger.error("DB connection is null! Cannot process deposit for address: " + to);
                return;
            }
            con.setAutoCommit(false);

            // Find user by address
            String queryAddress = "SELECT user_id FROM users WHERE address=?";
            Long userId = null;
            try (PreparedStatement pstmtAddr = con.prepareStatement(queryAddress)) {
                pstmtAddr.setString(1, to);
                try (ResultSet rsAddr = pstmtAddr.executeQuery()) {
                    if (rsAddr == null || !rsAddr.next()) {
                        //logger.warn("No user found for address: " + to);
                        return;
                    }
                    userId = rsAddr.getLong("user_id");
                    if (userId == null) {
                        //logger.warn("user_id is null for address: " + to);
                        return;
                    }
                }
            }

            // Check if transaction already exists
            String queryCheck = "SELECT COUNT(*) as total FROM trans WHERE txid=?";
            try (PreparedStatement pstmtCheck = con.prepareStatement(queryCheck)) {
                pstmtCheck.setString(1, txHash);
                try (ResultSet rsCheck = pstmtCheck.executeQuery()) {
                    int total = 0;
                    if (rsCheck != null && rsCheck.next()) {
                        total = rsCheck.getInt("total");
                    }
                    if (total > 0) {
                        logger.info("Transaction already processed: " + txHash);
                        return;
                    }
                }
            }

            // Insert transaction
            String queryInsert = "INSERT INTO trans SET userid=?, amount=?, stat=5, createddate=NOW(), type=1, txid=?, fromaddr=?, paymentmethod=1";
            try (PreparedStatement pstmtInsert = con.prepareStatement(queryInsert)) {
                pstmtInsert.setLong(1, userId);
                pstmtInsert.setBigDecimal(2, amount);
                pstmtInsert.setString(3, txHash);
                pstmtInsert.setString(4, from);
                pstmtInsert.execute();
            }

            // Update user balance
            String queryUpBal = "UPDATE users SET balance = balance + ? WHERE user_id=?";
            try (PreparedStatement pstmtUp = con.prepareStatement(queryUpBal)) {
                pstmtUp.setBigDecimal(1, amount);
                pstmtUp.setLong(2, userId);
                pstmtUp.execute();
            }

            // Update upline totals safely
            updateUplineTotal(con, userId, amount);

            // Insert history balance
            String queryHist = "INSERT INTO hist_bal SET amount=?, balance=(SELECT balance FROM users WHERE user_id=?), createddate=NOW(), user_id=?, trans_id=1, detail_desc=?";
            try (PreparedStatement pstmtHist = con.prepareStatement(queryHist)) {
                pstmtHist.setBigDecimal(1, amount);
                pstmtHist.setLong(2, userId);
                pstmtHist.setLong(3, userId);
                pstmtHist.setString(4, txHash);
                pstmtHist.execute();
            }

            // Commit all DB changes
            con.commit();

            // Sweep deposit
            try {
                WalletService walletService = new WalletService();
                DerivedWallet derivedWallet = walletService.deriveWallet(userId.intValue());
                walletService.sweepDeposit(to, amount, derivedWallet.getPrivateKey());
            } catch (Exception e) {
                logger.error("Error sweeping deposit for tx: " + txHash, e);
            }

            logger.info("Deposit processed successfully for tx: " + txHash);

        } catch (Exception e) {
            logger.error("Error processing deposit for address " + to + ", tx: " + txHash, e);
            try {
                if (con != null) con.rollback();
            } catch (Exception rollbackEx) {
                logger.error("Rollback failed", rollbackEx);
            }
        } finally {
            try {
                if (con != null) con.close();
            } catch (Exception closeEx) {
                logger.error("Failed to close connection", closeEx);
            }
        }
    }


    private static void updateUplineTotal(Connection con, Long userId, BigDecimal amount) throws Exception {
        String spcode = null;

        // Get first upline
        try (PreparedStatement pstmtUpline = con.prepareStatement("SELECT refuser FROM users WHERE user_id=?")) {
            pstmtUpline.setLong(1, userId);
            try (ResultSet rs = pstmtUpline.executeQuery()) {
                if (rs.next()) spcode = rs.getString("refuser");
            }
        }

        while (spcode != null) {
            // Update totaldepo for this upline
            try (PreparedStatement pstmtUpdate = con.prepareStatement("UPDATE users SET totaldepo = totaldepo + ? WHERE sponsorcode=?")) {
                pstmtUpdate.setBigDecimal(1, amount);
                pstmtUpdate.setString(2, spcode);
                pstmtUpdate.execute();
            }

            // Move to next upline
            try (PreparedStatement pstmtNext = con.prepareStatement("SELECT refuser FROM users WHERE sponsorcode=?")) {
                pstmtNext.setString(1, spcode);
                try (ResultSet rsNext = pstmtNext.executeQuery()) {
                    if (rsNext.next()) spcode = rsNext.getString("refuser");
                    else spcode = null;
                }
            }
        }
    }
}

