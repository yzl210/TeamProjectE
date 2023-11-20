package cn.leomc.teamprojecte;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.UsernameCache;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class TPCommand {

    public static final Multimap<UUID, UUID> INVITATIONS = HashMultimap.create();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("team_projecte")
                .then(Commands.literal("invite")
                        .requires(TPCommand::requiresPlayer)
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(TPCommand::invite)))
                .then(Commands.literal("leave")
                        .requires(TPCommand::requiresInTeam)
                        .executes(TPCommand::leave))
                .then(Commands.literal("transfer_ownership")
                        .requires(TPCommand::requiresOwner)
                        .then(Commands.argument("member", EntityArgument.player())
                                .executes(TPCommand::transferOwnership)))
                .then(Commands.literal("members")
                        .requires(TPCommand::requiresInTeam)
                        .executes(TPCommand::members))
                .then(Commands.literal("kick")
                        .requires(TPCommand::requiresOwner)
                        .then(Commands.argument("members", EntityArgument.players())
                                .executes(TPCommand::kick)))
                .then(Commands.literal("accept")
                        .requires(TPCommand::requiresPlayer)
                        .then(Commands.argument("team", UuidArgument.uuid())
                                .suggests(TPCommand::createSuggestionsForInvitation)
                                .executes(TPCommand::accept)))
                .then(Commands.literal("decline")
                        .requires(TPCommand::requiresPlayer)
                        .then(Commands.argument("team", UuidArgument.uuid())
                                .suggests(TPCommand::createSuggestionsForInvitation)
                                .executes(TPCommand::decline)))
                .then(Commands.literal("settings")
                        .then(Commands.literal("share_emc")
                                .executes(TPCommand::queryShareEMC)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .requires(TPCommand::requiresOwner)
                                        .executes(TPCommand::setShareEMC)))
                        .then(Commands.literal("share_knowledge")
                                .executes(TPCommand::queryShareKnowledge)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .requires(TPCommand::requiresOwner)
                                        .executes(TPCommand::setShareKnowledge))))
        );
    }


    private static boolean requiresPlayer(CommandSourceStack stack) {
        return stack.getEntity() instanceof ServerPlayer;
    }


    private static boolean requiresInTeam(CommandSourceStack stack) {
        if (stack.getEntity() instanceof ServerPlayer player) {
            TPTeam team = TPTeam.getTeamByMember(TeamProjectE.getPlayerUUID(player));
            return team != null && (!team.getOwner().equals(TeamProjectE.getPlayerUUID(player)) || !team.getMembers().isEmpty());
        }
        return false;
    }

    private static boolean requiresOwner(CommandSourceStack stack) {
        if (!requiresInTeam(stack))
            return false;
        if (stack.getEntity() instanceof ServerPlayer player) {
            TPTeam team = TPTeam.getTeamByMember(TeamProjectE.getPlayerUUID(player));
            return team != null && TeamProjectE.getPlayerUUID(player).equals(team.getOwner());
        }
        return false;
    }


    private static int transferOwnership(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null || !checkOwner(team, player))
            return 0;

        ServerPlayer newOwner = EntityArgument.getPlayer(context, "member");
        UUID newOwnerUUID = TeamProjectE.getPlayerUUID(newOwner);

        if (!team.getAll().contains(newOwnerUUID)) {
            context.getSource().sendFailure(Component.translatable("commands.teamprojecte.transfer_ownership.not_in_team"));
            return 0;
        }
        if (team.getOwner().equals(newOwnerUUID)) {
            context.getSource().sendFailure(Component.translatable("commands.teamprojecte.transfer_ownership.already_owner"));
            return 0;
        }

        team.transferOwner(newOwnerUUID);
        newOwner.sendSystemMessage(Component.translatable("commands.teamprojecte.transfer_ownership.new_owner").withStyle(ChatFormatting.GREEN));
        context.getSource().sendSuccess(Component.translatable("commands.teamprojecte.transfer_ownership.success", newOwner.getName()), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int kick(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null || !checkOwner(team, player))
            return 0;

        List<ServerPlayer> kick = EntityArgument.getPlayers(context, "members").stream()
                .filter(p -> team.getMembers().contains(TeamProjectE.getPlayerUUID(p)))
                .toList();
        kick.forEach(p -> {
            team.removeMember(TeamProjectE.getPlayerUUID(p));
            p.sendSystemMessage(Component.translatable("commands.teamprojecte.kicked").withStyle(ChatFormatting.RED));
        });

        if (!kick.isEmpty())
            context.getSource().sendSuccess(Component.translatable("commands.teamprojecte.kick.success", kick.size()), true);
        else
            context.getSource().sendFailure(Component.translatable("commands.teamprojecte.players_not_found"));

        return kick.size();
    }

    private static int members(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null)
            return 0;

        player.sendSystemMessage(Component.translatable("commands.teamprojecte.members", getNames(team.getOwner(), team.getAll())));
        return Command.SINGLE_SUCCESS;
    }

    private static Component getNames(UUID owner, List<UUID> uuids) {
        List<Component> components = new ArrayList<>();
        PlayerList playerList = ServerLifecycleHooks.getCurrentServer().getPlayerList();

        for (UUID uuid : uuids) {
            MutableComponent component;
            ServerPlayer player = playerList.getPlayer(uuid);
            if (player != null)
                component = player.getName().copy()
                        .withStyle(ChatFormatting.GREEN)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("commands.teamprojecte.members.member_online"))));
            else if (UsernameCache.containsUUID(uuid))
                component = Component.literal(UsernameCache.getLastKnownUsername(uuid))
                        .withStyle(ChatFormatting.RED)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("commands.teamprojecte.members.member_offline"))));
            else
                component = Component.literal(uuid.toString())
                        .withStyle(ChatFormatting.GRAY)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("commands.teamprojecte.members.member_unknown"))));
            if (uuid.equals(owner))
                component.withStyle(ChatFormatting.BOLD);
            components.add(component);
        }
        return ComponentUtils.formatList(components, c -> c);
    }

    private static int leave(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null)
            return 0;
        team.removeMember(TeamProjectE.getPlayerUUID(player));
        return Command.SINGLE_SUCCESS;
    }

    private static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        UUID uuid = UuidArgument.getUuid(context, "team");
        if (!INVITATIONS.get(TeamProjectE.getPlayerUUID(player)).contains(uuid)) {
            context.getSource().sendFailure(Component.translatable("commands.teamprojecte.invitation.not_found"));
            return -1;
        }

        INVITATIONS.remove(TeamProjectE.getPlayerUUID(player), uuid);

        TPTeam team = TPTeam.getTeam(uuid);
        if (team == null) {
            context.getSource().sendFailure(Component.translatable("commands.teamprojecte.team_not_found"));
            return -1;
        }
        TPTeam originalTeam = TPTeam.getTeamByMember(TeamProjectE.getPlayerUUID(player));
        if (originalTeam != null)
            team.addMemberWithKnowledge(originalTeam, player);
        else
            team.addMember(TeamProjectE.getPlayerUUID(player));

        context.getSource().sendSuccess(Component.translatable("commands.teamprojecte.invite.accepted").withStyle(ChatFormatting.GREEN), false);
        Component component = Component.translatable("commands.teamprojecte.joined_team", player.getDisplayName()).withStyle(ChatFormatting.GREEN);
        TeamProjectE.getAllOnline(team.getAll()).forEach(p -> p.sendSystemMessage(component));

        return Command.SINGLE_SUCCESS;
    }


    private static int decline(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        UUID uuid = UuidArgument.getUuid(context, "team");
        if (!INVITATIONS.get(TeamProjectE.getPlayerUUID(player)).contains(uuid)) {
            context.getSource().sendFailure(Component.translatable("commands.teamprojecte.invitation.not_found"));
            return -1;
        }

        INVITATIONS.remove(TeamProjectE.getPlayerUUID(player), uuid);

        TPTeam team = TPTeam.getTeam(uuid);
        if (team == null) {
            context.getSource().sendFailure(Component.translatable("commands.teamprojecte.team_not_found"));
            return -1;
        }

        player.sendSystemMessage(Component.translatable("commands.teamprojecte.invite.declined").withStyle(ChatFormatting.RED));
        TeamProjectE.getAllOnline(Collections.singletonList(team.getOwner())).forEach(p ->
                p.sendSystemMessage(Component.translatable("commands.teamprojecte.invitation.declined", player.getDisplayName())));

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> createSuggestionsForInvitation(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        Player player = checkPlayer(context);
        return SharedSuggestionProvider.suggest(INVITATIONS.get(TeamProjectE.getPlayerUUID(player)).stream().map(UUID::toString), builder);
    }

    private static int invite(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Player player = checkPlayer(context);
        TPTeam team = TPTeam.getOrCreateTeam(TeamProjectE.getPlayerUUID(player));


        Collection<ServerPlayer> players =
                EntityArgument.getPlayers(context, "players").stream()
                        .filter(p -> !team.getAll().contains(TeamProjectE.getPlayerUUID(p)))
                        .toList();

        Component component = Component.translatable("commands.teamprojecte.invitation",
                player.getDisplayName(),
                Component.translatable("commands.teamprojecte.invite.option.accept")
                        .withStyle(style -> style.applyFormat(ChatFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team_projecte accept " + team.getUUID()))),
                Component.translatable("commands.teamprojecte.invite.option.decline")
                        .withStyle(style -> style.applyFormat(ChatFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team_projecte decline " + team.getUUID())))
        );

        for (ServerPlayer p : players) {
            INVITATIONS.put(TeamProjectE.getPlayerUUID(p), team.getUUID());
            p.sendSystemMessage(component);
        }
        if (!players.isEmpty())
            context.getSource().sendSuccess(Component.translatable("commands.teamprojecte.invite.success", players.size()), true);
        else
            context.getSource().sendFailure(Component.translatable("commands.teamprojecte.players_not_found"));
        return players.size();
    }

    private static ServerPlayer checkPlayer(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        if (context.getSource().getEntity() instanceof ServerPlayer player)
            return player;
        throw CommandSourceStack.ERROR_NOT_PLAYER.create();
    }

    private static TPTeam checkInTeam(Player player) {
        TPTeam team = TPTeam.getTeamByMember(TeamProjectE.getPlayerUUID(player));
        if (team == null || (team.getOwner().equals(TeamProjectE.getPlayerUUID(player)) && team.getMembers().isEmpty())) {
            player.sendSystemMessage(Component.translatable("commands.teamprojecte.leave.not_in_team").withStyle(ChatFormatting.RED));
            return null;
        }
        return team;
    }

    private static boolean checkOwner(TPTeam team, ServerPlayer player) {
        if (!TeamProjectE.getPlayerUUID(player).equals(team.getOwner())) {
            player.sendSystemMessage(Component.translatable("commands.teamprojecte.not_owner").withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static int queryShareEMC(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null)
            return 0;

        context.getSource().sendSuccess(Component.translatable("commands.teamprojecte.settings.query.sharing_emc." + team.isSharingEMC()), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setShareEMC(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null || !checkOwner(team, player))
            return 0;

        team.setShareEMC(BoolArgumentType.getBool(context, "value"));
        context.getSource().sendSuccess(Component.translatable("commands.teamprojecte.settings.set.sharing_emc." + team.isSharingEMC()), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int queryShareKnowledge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null)
            return 0;

        context.getSource().sendSuccess(Component.translatable("commands.teamprojecte.settings.query.sharing_knowledge." + team.isSharingKnowledge()), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setShareKnowledge(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null || !checkOwner(team, player))
            return 0;

        team.setShareKnowledge(BoolArgumentType.getBool(context, "value"));
        context.getSource().sendSuccess(Component.translatable("commands.teamprojecte.settings.set.sharing_knowledge." + team.isSharingKnowledge()), true);

        return Command.SINGLE_SUCCESS;
    }

}