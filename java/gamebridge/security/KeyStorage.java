package gamebridge.security;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import gamebridge.GameBridge;

public class KeyStorage implements AutoCloseable {
	
	private Connection c;
	
	public KeyStorage(File f) throws SQLException {
		load(f);
	}
	
	private void load(File f) throws SQLException {
		if (c == null) {
			if (!f.exists()) try {
				
				GameBridge.info("Creating a new database to store keys...");
				
				f.createNewFile();
				Thread.sleep(2000l);
				
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
			var path = f.getAbsolutePath();
			
			this.c = DriverManager.getConnection("jdbc:sqlite:" + path);
			
			c.prepareStatement("CREATE TABLE IF NOT EXISTS keystore(name VARCHAR, key TINYBLOB)").executeUpdate();
		}
	}
	
	public byte[] getKey(String name) throws SQLException {
		var ps = c.prepareStatement("SELECT key FROM keystore WHERE name = ?");
		ps.setString(1, name);
		var rs = ps.executeQuery();
		if (!rs.next()) return null;
		return rs.getBytes("key");
	}
	
	public void setKey(String name, byte[] key) throws SQLException {
		if (getKey(name) == null) {
			var ps = c.prepareStatement("INSERT INTO keystore VALUES(?, ?)");
			ps.setString(1, name);
			ps.setBytes(2, key);
			ps.executeUpdate();
			return;
		}
		
		var ps = c.prepareStatement("UPDATE keystore SET key = ? WHERE name = ?");
		ps.setBytes(1, key);
		ps.setString(2, name);
		ps.executeUpdate();
	}

	@Override
	public void close() throws Exception {
		c.close();
	}

}
