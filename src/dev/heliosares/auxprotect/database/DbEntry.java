package dev.heliosares.auxprotect.database;

import javax.annotation.Nonnull;

import org.bukkit.Location;

public class DbEntry {

	private final long time;

	private final EntryAction action;
	private final boolean state;
	private String data;

	public final String world;
	public final int x;
	public final int y;
	public final int z;

	public final int pitch;
	public final int yaw;

	protected String userLabel;
	private String user;
	private int uid;

	private String targetLabel;
	private String target;
	private int target_id;

	private boolean hasBlob;
	private byte[] blob;

	private DbEntry(String userLabel, EntryAction action, boolean state, String world, int x, int y, int z, int pitch,
			int yaw, String targetLabel, String data) {
		this.time = DatabaseRunnable.getTime(action.getTable());
		this.userLabel = userLabel;
		this.action = action;
		this.state = state;
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.pitch = pitch;
		this.yaw = yaw;
		this.targetLabel = targetLabel;
		this.data = data;
	}

	/**
	 * 
	 * @param userLabel
	 * @param action
	 * @param state
	 * @param targetLabel
	 * @param data
	 */
	public DbEntry(String userLabel, EntryAction action, boolean state, String targetLabel, String data) {
		this(userLabel, action, state, null, 0, 0, 0, 0, 0, targetLabel, data);
	}

	/**
	 * @param userLabel   The label of the user, provided by
	 *                    AuxProtect#getLabel(Object) and
	 *                    AuxProtectBungee#getLabel(Object) as applicable. This may
	 *                    also be a generic string such as "#env"
	 * 
	 * @param state       Specifies the state of EntryAction (i.e +mount vs -mount),
	 *                    if applicable, otherwise false.
	 * 
	 * @param location    Should not be null. Will throw NullPointerException
	 * 
	 * @param targetLabel The label of the target, see userLabel for details.
	 * 
	 * @param data        Extra data about your entry. This is stored as plain text
	 *                    so use sparingly.
	 * 
	 * @throws NullPointerException
	 */
	public DbEntry(String userLabel, EntryAction action, boolean state, @Nonnull Location location, String targetLabel,
			String data) throws NullPointerException {
		this(userLabel, action, state, location.getWorld().getName(), location.getBlockX(), location.getBlockY(),
				location.getBlockZ(), (int) Math.round(location.getPitch()), (int) Math.round(location.getYaw()),
				targetLabel, data);
	}

	protected DbEntry(long time, int uid, EntryAction action, boolean state, String world, int x, int y, int z,
			int pitch, int yaw, String target, int target_id, String data) {
		this.time = time;
		this.uid = uid;
		this.action = action;
		this.state = state;
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.pitch = pitch;
		this.yaw = yaw;
		this.targetLabel = target;
		this.target_id = target_id;
		this.data = data;
	}

	public long getTime() {
		return time;
	}

	public EntryAction getAction() {
		return action;
	}

	public boolean getState() {
		return state;
	}

	public String getData() {
		return data;
	}

	public void setData(String data) {
		this.data = data;
	}

	public String getUser() {
		if (user != null) {
			return user;
		}
		if (!getUserUUID().startsWith("$") || getUserUUID().length() != 37) {
			return user = getUserUUID();
		}
		user = SQLManager.getInstance().getUsernameFromUID(getUid());
		if (user == null) {
			user = getUserUUID();
		}
		return user;
	}

	public int getUid() {
		if (uid > 0) {
			return uid;
		}
		return uid = SQLManager.getInstance().getUIDFromUUID(getUserUUID(), true);
	}

	public int getTargetId() {
		if (action.getTable().hasStringTarget()) {
			return -1;
		}
		if (target_id > 0) {
			return target_id;
		}
		return target_id = SQLManager.getInstance().getUIDFromUUID(getTargetUUID(), true);
	}

	public String getTarget() {
		if (target != null) {
			return target;
		}
		if (action.getTable().hasStringTarget() || !getTargetUUID().startsWith("$") || getTargetUUID().length() != 37) {
			return target = getTargetUUID();
		}
		target = SQLManager.getInstance().getUsernameFromUID(getTargetId());
		if (target == null) {
			target = getTargetUUID();
		}
		return target;
	}

	public String getTargetUUID() {
		if (targetLabel != null) {
			return targetLabel;
		}
		if (target_id > 0) {
			targetLabel = SQLManager.getInstance().getUUIDFromUID(target_id);
		} else if (target_id == 0) {
			return targetLabel = "";
		}
		if (targetLabel == null) {
			targetLabel = "#null";
		}
		return targetLabel;
	}

	public String getUserUUID() {
		if (userLabel != null) {
			return userLabel;
		}
		if (uid > 0) {
			userLabel = SQLManager.getInstance().getUUIDFromUID(uid);
		} else if (uid == 0) {
			return userLabel = "";
		}
		if (userLabel == null) {
			userLabel = "#null";
		}
		return userLabel;
	}

	public double getBoxDistance(DbEntry entry) {
		if (!entry.world.equals(world)) {
			return -1;
		}
		return Math.max(Math.max(Math.abs(entry.x - x), Math.abs(entry.y - y)), Math.abs(entry.z - z));
	}

	public double getDistance(DbEntry entry) {
		return Math.sqrt(getDistanceSq(entry));
	}

	public double getDistanceSq(DbEntry entry) {
		return Math.pow(x - entry.x, 2) + Math.pow(y - entry.y, 2) + Math.pow(z - entry.z, 2);
	}

	public byte[] getBlob() {
		if (blob == null) {
			blob = SQLManager.getInstance().getBlob(this);
		}
		return blob;
	}

	public void setBlob(byte[] blob) {
		this.blob = blob;
	}

	public boolean hasBlob() {
		return hasBlob || blob != null;
	}

	public void setHasBlob() {
		hasBlob = true;
	}
}
