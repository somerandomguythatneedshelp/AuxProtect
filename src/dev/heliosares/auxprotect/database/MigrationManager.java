package dev.heliosares.auxprotect.database;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.utils.InvSerialization;

public class MigrationManager {
	private final SQLManager sql;
	private final IAuxProtect plugin;
	private boolean isMigrating;

	private int rowcountformerge;

	MigrationManager(SQLManager sql, IAuxProtect plugin) {
		this.sql = sql;
		this.plugin = plugin;
	}

	boolean isMigrating() {
		return isMigrating;
	}

	void preTables() throws SQLException, IOException {
		if (sql.getVersion() < SQLManager.DBVERSION) {
			plugin.info("Outdated DB Version: " + sql.getVersion() + ". Migrating to version: " + SQLManager.DBVERSION
					+ "...");
			plugin.info("This may take a while. Please do not interrupt.");
			isMigrating = true;
			if (!sql.isMySQL()) {
				plugin.info("Pre-migration database backup created: " + sql.backup());
			}
		}

		if (sql.getVersion() < 5) {
			try {
				sql.execute("ALTER TABLE " + SQLManager.getTablePrefix() + "auxprotect RENAME TO "
						+ Table.AUXPROTECT_MAIN.toString());
			} catch (SQLException ignored) {
				plugin.warning(
						"Failed to rename auxprotect table for migration. This may cause errors. Migration continuing.");
			}
		}

		if (sql.getVersion() < 2 && !plugin.isBungee()) {
			plugin.info("Migrating database to v2");
			sql.execute("ALTER TABLE worlds RENAME TO auxprotect_worlds;");

			sql.setVersion(2);
		}

		if (sql.getVersion() < 3) {
			rowcountformerge = migrateToV3Part1();
		}
	}

	void postTables() throws SQLException {
		if (sql.getVersion() < 3) {
			migrateToV3Part2();
		}

		if (sql.getVersion() < 4) {
			migrateToV4();
		}

		if (sql.getVersion() < 5) {
			sql.setVersion(5);
		}

		if (sql.getVersion() < 6) {
			migrateToV6();
		}

		/*
		 * This should never be reached and is only here as a fail safe
		 */
		if (sql.getVersion() < SQLManager.DBVERSION) {
			sql.setVersion(SQLManager.DBVERSION);
		}

		plugin.debug("Purging temporary tables");
		for (Table table : Table.values()) {
			sql.execute("DROP TABLE IF EXISTS " + table.toString() + "temp;");
			sql.execute("DROP TABLE IF EXISTS " + table.toString() + "_temp;");
		}

		isMigrating = false;
	}

	int migrateToV3Part1() throws SQLException {
		Table[] migrateTablesV3 = new Table[] { Table.AUXPROTECT_MAIN, Table.AUXPROTECT_SPAM, Table.AUXPROTECT_LONGTERM,
				Table.AUXPROTECT_ABANDONED, Table.AUXPROTECT_INVENTORY };
		if (plugin.isBungee()) {
			migrateTablesV3 = new Table[] { Table.AUXPROTECT_MAIN, Table.AUXPROTECT_LONGTERM };
		}
		int rowcountformerge = 0;
		plugin.info("Migrating database to v3. DO NOT INTERRUPT");
		for (Table table : migrateTablesV3) {
			try {
				sql.execute("ALTER TABLE " + table.toString() + " RENAME TO " + table.toString() + "_temp;");
			} catch (Exception ignored) {
				plugin.warning("Error renaming table, continuing anyway. This may cause errors.");
			}
			rowcountformerge += sql.count(table + "_temp");
			plugin.info(".");
		}
		plugin.info("Tables renamed");
		return rowcountformerge;
	}

	void migrateToV3Part2() throws SQLException {
		Table[] migrateTablesV3 = new Table[] { Table.AUXPROTECT_MAIN, Table.AUXPROTECT_SPAM, Table.AUXPROTECT_LONGTERM,
				Table.AUXPROTECT_ABANDONED, Table.AUXPROTECT_INVENTORY };
		if (plugin.isBungee()) {
			migrateTablesV3 = new Table[] { Table.AUXPROTECT_MAIN, Table.AUXPROTECT_LONGTERM };
		}
		plugin.info("Merging data into new tables...");
		int progress = 0;
		int count = 0;

		for (Table table : migrateTablesV3) {
			ArrayList<Object[]> output = new ArrayList<>();
			ArrayList<Object[]> commands = new ArrayList<>();
			final boolean hasLocation = plugin.isBungee() ? false : table.hasLocation();
			final boolean hasData = table.hasData();
			final boolean hasStringTarget = table.hasStringTarget();
			plugin.info("Merging table: " + table.toString());
			String stmt = "SELECT * FROM " + table.toString() + "_temp;";
			plugin.debug(stmt, 3);
			try (PreparedStatement pstmt = sql.connection.prepareStatement(stmt)) {
				pstmt.setFetchSize(500);
				try (ResultSet results = pstmt.executeQuery()) {
					while (results.next()) {
						ArrayList<Object> entry = new ArrayList<>();
						entry.add(results.getLong("time"));
						entry.add(sql.getUIDFromUUID(results.getString("user"), true));
						int action_id = results.getInt("action_id");
						if (action_id != 260) {
							entry.add(action_id);
						}
						if (hasLocation) {
							entry.add(results.getInt("world_id"));
							entry.add(results.getInt("x"));
							entry.add(results.getInt("y"));
							entry.add(results.getInt("z"));
						}
						String target = results.getString("target");
						if (hasStringTarget || action_id == 260) {
							entry.add(target);
						} else {
							entry.add(sql.getUIDFromUUID(target, true));
						}
						if (hasData) {
							entry.add(results.getString("data"));
						}

						if (action_id == 260) {
							commands.add(entry.toArray(new Object[0]));
						} else {
							output.add(entry.toArray(new Object[0]));
						}
						if (output.size() >= 5000) {
							putRaw(table, output);
							output.clear();
						}
						if (commands.size() >= 5000) {
							putRaw(Table.AUXPROTECT_COMMANDS, commands);
							commands.clear();
						}
						count++;
						int progressPercentage = (int) Math.floor((double) count / rowcountformerge * 100);
						if (progressPercentage / 5 > progress) {
							progress = progressPercentage / 5;
							plugin.info("Migration " + progress * 5 + "% complete. (" + count + "/" + rowcountformerge
									+ "). DO NOT INTERRUPT");
						}
					}
				}
			}
			if (output.size() > 0) {
				putRaw(table, output);
			}
			if (commands.size() > 0) {
				putRaw(Table.AUXPROTECT_COMMANDS, commands);
			}
		}

		sql.setVersion(3);
	}

	void migrateToV4() throws SQLException {
		plugin.info("Migrating database to v4. DO NOT INTERRUPT");
		if (!plugin.isBungee()) {
			ArrayList<Object[]> output = new ArrayList<>();
			String stmt = "SELECT * FROM " + Table.AUXPROTECT_SPAM.toString() + " WHERE action_id = 256;";
			plugin.debug(stmt, 3);
			try (PreparedStatement pstmt = sql.connection.prepareStatement(stmt)) {
				pstmt.setFetchSize(500);
				try (ResultSet results = pstmt.executeQuery()) {
					while (results.next()) {
						ArrayList<Object> entry = new ArrayList<>();
						entry.add(results.getLong("time"));
						entry.add(results.getInt("uid"));
						entry.add(EntryAction.POS.id);
						entry.add(results.getInt("world_id"));
						entry.add(results.getInt("x"));
						entry.add(results.getInt("y"));
						entry.add(results.getInt("z"));
						String data = results.getString("data");

						try {
							String parts[] = data.split("[^\\d-]+");
							entry.add(Integer.parseInt(parts[2]));
							entry.add(Integer.parseInt(parts[1]));
						} catch (Exception e) {
							plugin.print(e);
						}

						entry.add(results.getInt("target_id"));
						output.add(entry.toArray(new Object[0]));

						if (output.size() >= 5000) {
							putRaw(Table.AUXPROTECT_POSITION, output);
							output.clear();
						}
					}
				}
			}
			if (output.size() > 0) {
				putRaw(Table.AUXPROTECT_POSITION, output);
			}
			plugin.info("Deleting old entries.");
			sql.execute("DELETE FROM " + Table.AUXPROTECT_SPAM.toString() + " WHERE action_id = 256;");
		}
		sql.setVersion(4);
	}

	@SuppressWarnings("deprecation")
	void migrateToV6() throws SQLException {
		if (!plugin.isBungee()) {
			try {
				sql.execute("ALTER TABLE " + Table.AUXPROTECT_INVENTORY.toString() + " ADD COLUMN hasblob BOOL");
			} catch (SQLException e) {
				plugin.info("This is probably fine..");
				plugin.print(e);
			}

			final int totalrows = sql.count(Table.AUXPROTECT_INVENTORY);
			long lastupdate = 0;
			int count = 0;

			String stmt = "SELECT time, action_id, data FROM " + Table.AUXPROTECT_INVENTORY.toString();
			plugin.debug(stmt, 3);
			HashMap<Long, byte[]> blobs = new HashMap<>();
			try (PreparedStatement pstmt = sql.connection.prepareStatement(stmt)) {
				pstmt.setFetchSize(500);
				try (ResultSet results = pstmt.executeQuery()) {
					while (results.next()) {
						int progress = (int) Math.round((double) count / (double) totalrows * 100.0);
						if (System.currentTimeMillis() - lastupdate > 5000) {
							lastupdate = System.currentTimeMillis();
							plugin.info("Migration " + progress + "% complete. (" + count + "/" + totalrows
									+ "). DO NOT INTERRUPT");
						}
						count++;
						long time = results.getLong("time");
						String data = results.getString("data");
						int action_id = results.getInt("action_id");
						boolean hasblob = false;
						if (data.contains(InvSerialization.ITEM_SEPARATOR)) {
							data = data.substring(data.indexOf(InvSerialization.ITEM_SEPARATOR)
									+ InvSerialization.ITEM_SEPARATOR.length());
							hasblob = true;
						}
						byte[] blob = null;
						try {
							if (action_id == EntryAction.INVENTORY.id) {
								try {
									blob = InvSerialization.playerToByteArray(InvSerialization.toPlayer(data));
								} catch (Exception e) {
									plugin.warning("THIS IS PROBABLY FINE. Failed to migrate inventory log at " + time
											+ "e. This can be ignored, but this entry will no longer be available.");
								}
							} else {
								if (!hasblob) {
									continue;
								}
								blob = Base64Coder.decodeLines(data);
							}
						} catch (IllegalArgumentException e) {
							plugin.info("Error while decoding: " + data);
							throw e;
						}
						if (blob == null || blob.length == 0) {
							continue;
						}

						blobs.put(time, blob);
						if (blobs.size() >= 1000) {
							migrateV6Commit(blobs);
							blobs.clear();
						}
					}
				}
			}
			migrateV6Commit(blobs);
		}
		sql.setVersion(6);
	}

	void putRaw(Table table, ArrayList<Object[]> datas)
			throws SQLException, ClassCastException, IndexOutOfBoundsException {
		sql.checkAsync();
		synchronized (sql.connection) {
			sql.holdingConnectionSince = System.currentTimeMillis();
			sql.holdingConnection = "put";
			String stmt = "INSERT INTO " + table.toString() + " ";
			final boolean hasLocation = plugin.isBungee() ? false : table.hasLocation();
			final boolean hasData = table.hasData();
			final boolean hasAction = table.hasActionId();
			final boolean hasLook = table.hasLook();
			stmt += table.getValuesHeader(plugin.isBungee());
			String inc = table.getValuesTemplate(plugin.isBungee());
			stmt += " VALUES";
			for (int i = 0; i < datas.size(); i++) {
				stmt += "\n" + inc;
				if (i + 1 == datas.size()) {
					stmt += ";";
				} else {
					stmt += ",";
				}
			}
			try (PreparedStatement statement = sql.connection.prepareStatement(stmt)) {

				int i = 1;
				for (Object[] data : datas) {
					int y = 0;
					try {
						// statement.setString(i++, table);
						statement.setLong(i++, (long) data[y++]);
						statement.setInt(i++, (int) data[y++]);

						if (hasAction) {
							statement.setInt(i++, (int) data[y++]);
						}
						if (hasLocation) {
							statement.setInt(i++, (int) data[y++]);
							statement.setInt(i++, (int) data[y++]);
							statement.setInt(i++, (int) data[y++]);
							statement.setInt(i++, (int) data[y++]);
						}
						if (hasLook) {
							statement.setInt(i++, (int) data[y++]);
							statement.setInt(i++, (int) data[y++]);
						}
						if (table.hasStringTarget()) {
							statement.setString(i++, (String) data[y++]);
						} else {
							statement.setInt(i++, (int) data[y++]);
						}
						if (hasData) {
							statement.setString(i++, (String) data[y++]);
						}
					} catch (Exception e) {
						String error = "";
						for (Object o : data) {
							error += o + ", ";
						}
						plugin.warning(error + "\nError at index " + y);
						throw e;
					}
				}

				statement.executeUpdate();
			}
			sql.rowcount += datas.size();
			sql.holdingConnectionSince = 0;
		}
	}

	private void migrateV6Commit(HashMap<Long, byte[]> blobs) throws SQLException {
		sql.putBlobs(blobs);

		String where = " WHERE time IN (";
		for (Long time : blobs.keySet()) {
			where += time + ",";
		}
		where = where.substring(0, where.length() - 1) + ")";
		sql.execute("UPDATE " + Table.AUXPROTECT_INVENTORY.toString() + " SET hasblob=1, data = ''" + where);
	}
}
