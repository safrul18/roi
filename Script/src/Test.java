import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.log4j.Logger;

import com.util.DBUtil;
import com.util.DerivedWallet;
import com.util.WalletService;

public class Test {
	
	private static Logger logger = Logger.getLogger(Test.class);
	
	public static void main(String[] args) throws Exception {

		//save to db
		try {
			Connection con = DBUtil.getConnection();
			WalletService walletService = new WalletService();
			String query = "select * from users where address is null";
			
			PreparedStatement pstmt = con.prepareStatement(query);
			ResultSet rs = pstmt.executeQuery();
			while(rs.next()) {
				logger.info("user:"+rs.getLong("user_id"));
				DerivedWallet wallet = walletService.deriveWallet(rs.getInt("user_id"));
				String queryInsert = "update users set address=? where user_id=?";
				PreparedStatement pstmtInsert = con.prepareStatement(queryInsert);
				pstmtInsert.setString(1, wallet.getAddress());
				pstmtInsert.setLong(2, rs.getLong("user_id"));
				pstmtInsert.execute();
				
				con.commit();
			}
			
			

		
	}catch (Exception e) {
		// TODO: handle exception
	}
	}
}
