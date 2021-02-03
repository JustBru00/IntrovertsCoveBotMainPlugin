package net.introvertscove.survivalserver.plugin.listeners;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.introvertscove.survivalserver.beans.MemberDataBean;
import net.introvertscove.survivalserver.plugin.IntrovertsPlugin;
import net.introvertscove.survivalserver.plugin.database.DatabaseManager;
import net.introvertscove.survivalserver.plugin.utils.Messager;
import net.introvertscove.survivalserver.plugin.utils.SpectatorAccountsOptions;
import net.introvertscove.survivalserver.plugin.utils.UUIDFetcher;

public class PlayerLoginLogoutListener implements Listener {

	@EventHandler
	public void onPlayerPreLogin(AsyncPlayerPreLoginEvent e) {
		final UUID playerUuid = e.getUniqueId();
		if (playerUuid != null) {
			UUIDFetcher.updateCachedUuid(playerUuid, e.getName());
			
			Optional<MemberDataBean> memberData = DatabaseManager.getMemberData(playerUuid);

			if (!memberData.isPresent()) {
				// NOT A MEMBER
				// CHECK SPECTATOR ACCOUNTS
				if (SpectatorAccountsOptions.doAllowNonMembersAsSpectator()
						|| (!SpectatorAccountsOptions.isSpectatorAccountsDisabled()
								&& DatabaseManager.isSpectatorAccount(playerUuid))) {
					// is a spectator
					return;
				}

				e.setLoginResult(Result.KICK_WHITELIST);
				e.setKickMessage(Messager.color(
						"&cSorry your account is not on the member list for the Introvert's Cove.\n&cSee &fhttps://www.introvertscove.net/applications &cto apply to join the server.\n\n&cIf you believe this is in error please contact us at contact@introvertcove.com."));
				return;
			}

		}

	}

	@EventHandler
	public void onPlayerLogin(PlayerJoinEvent e) {
		final UUID playerId = e.getPlayer().getUniqueId();
		DatabaseManager.logPlayerLoginToSessionHistory(playerId);
		Optional<MemberDataBean> memberData = DatabaseManager.getMemberData(playerId);

		if (!memberData.isPresent()) {
			if (DatabaseManager.isSpectatorAccount(playerId)
					&& SpectatorAccountsOptions.doForceSpectatorGamemodeOnJoin()) {
				Bukkit.getScheduler().scheduleSyncDelayedTask(IntrovertsPlugin.getInstance(), new Runnable() {

					public void run() {
						// Set to spectator gamemode
						try {
							Bukkit.getPlayer(playerId).setGameMode(GameMode.SPECTATOR);
						} catch (NullPointerException e) {
							Messager.msgConsole(
									"[PlayerLoginLogoutListener] Spectator player logged out before I could set them to spectator gamemode.");
						}
					}
				}, 5);
			}
			return;
		}

		memberData.get().setLastIpAddress(e.getPlayer().getAddress().getAddress().getHostAddress());

		DatabaseManager.saveMemberDataToFile(memberData.get());
	}

	@EventHandler
	public void onPlayerLogout(PlayerQuitEvent e) {
		final UUID playerId = e.getPlayer().getUniqueId();
		DatabaseManager.logPlayerLogoutToSessionHistory(e.getPlayer().getUniqueId());
		Optional<MemberDataBean> memberData = DatabaseManager.getMemberData(playerId);

		if (!memberData.isPresent()) {
			return;
		}

		memberData.get().getLimboStatus().setLastLogout(System.currentTimeMillis());

		DatabaseManager.saveMemberDataToFile(memberData.get());
	}

}