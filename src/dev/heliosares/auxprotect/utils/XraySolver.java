package dev.heliosares.auxprotect.utils;

import java.util.ArrayList;
import java.util.HashMap;

import dev.heliosares.auxprotect.core.IAuxProtect;
import dev.heliosares.auxprotect.database.DbEntry;
import dev.heliosares.auxprotect.database.EntryAction;
import dev.heliosares.auxprotect.database.XrayEntry;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention;
import net.md_5.bungee.api.chat.hover.content.Text;

public class XraySolver {

	public static BaseComponent[] solve(ArrayList<DbEntry> entries, IAuxProtect plugin) {
		ComponentBuilder message = new ComponentBuilder().append("", FormatRetention.NONE);
		HashMap<String, ArrayList<DbEntry>> hash = new HashMap<>();
		for (int i = entries.size() - 1; i >= 0; i--) {
			DbEntry entry = entries.get(i);
			ArrayList<DbEntry> hits = hash.get(entry.getUserUUID());
			if (hits == null) {
				hash.put(entry.getUserUUID(), hits = new ArrayList<>());
			}
			if (entry.getAction().equals(EntryAction.VEIN)) {
				hits.add(entry);
			}
		}

		for (ArrayList<DbEntry> entries1 : hash.values()) {
			int score = 0;
			for (DbEntry entry : entries1) {
				short rating = ((XrayEntry) entry).getRating();
				if (rating > 0) {
					score += rating;
				}
			}
			if (score >= 6 || hash.size() == 1) {
				String user = entries1.get(0).getUser();
				String tooltip = "�4Hits for '" + user + "':\n";
				for (DbEntry entry : entries1) {
					short severity = ((XrayEntry) entry).getRating();
					switch (severity) {
					case 1:
						tooltip += "�e";
						break;
					case 2:
						tooltip += "�c";
						break;
					case 3:
						tooltip += "�4";
						break;
					default:
						continue;
					}
					tooltip += "\n" + TimeUtil.millisToString(System.currentTimeMillis() - entry.getTime())
							+ " ago, severity " + severity;
				}

				message.append(String.format("�4�l%s�c - score %d / 6", user, score))
						.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(tooltip)))
						.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
								String.format("/ap lookup action:vein #xray user:%s", user)));
				message.append("\n").event((ClickEvent) null).event((HoverEvent) null);
			}
		}
		return message.create();
	}
}
