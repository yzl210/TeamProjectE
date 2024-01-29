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
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.UUIDArgument;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.Util;
import net.minecraft.util.text.*;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.common.UsernameCache;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TPCommand {

    public static final Multimap<UUID, UUID> INVITATIONS = HashMultimap.create();

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
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
                        .then(Commands.argument("team", UUIDArgument.uuid())
                                .suggests(TPCommand::createSuggestionsForInvitation)
                                .executes(TPCommand::accept)))
                .then(Commands.literal("decline")
                        .requires(TPCommand::requiresPlayer)
                        .then(Commands.argument("team", UUIDArgument.uuid())
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


    private static boolean requiresPlayer(CommandSource stack) {
        return stack.getEntity() instanceof ServerPlayerEntity;
    }


    private static boolean requiresInTeam(CommandSource stack) {
        if (stack.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) stack.getEntity();
            TPTeam team = TPTeam.getTeamByMember(TeamProjectE.getPlayerUUID(player));
            return team != null && (!team.getOwner().equals(TeamProjectE.getPlayerUUID(player)) || !team.getMembers().isEmpty());
        }
        return false;
    }

    private static boolean requiresOwner(CommandSource stack) {
        if (!requiresInTeam(stack))
            return false;
        if (stack.getEntity() instanceof ServerPlayerEntity) {
            ServerPlayerEntity player = (ServerPlayerEntity) stack.getEntity();
            TPTeam team = TPTeam.getTeamByMember(TeamProjectE.getPlayerUUID(player));
            return team != null && TeamProjectE.getPlayerUUID(player).equals(team.getOwner());
        }
        return false;
    }


    private static int transferOwnership(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null || !checkOwner(team, player))
            return 0;

        ServerPlayerEntity newOwner = EntityArgument.getPlayer(context, "member");
        UUID newOwnerUUID = TeamProjectE.getPlayerUUID(newOwner);

        if (!team.getAll().contains(newOwnerUUID)) {
            context.getSource().sendFailure(new TranslationTextComponent("commands.teamprojecte.transfer_ownership.not_in_team"));
            return 0;
        }
        if (team.getOwner().equals(newOwnerUUID)) {
            context.getSource().sendFailure(new TranslationTextComponent("commands.teamprojecte.transfer_ownership.already_owner"));
            return 0;
        }

        team.transferOwner(newOwnerUUID);
        newOwner.sendMessage(new TranslationTextComponent("commands.teamprojecte.transfer_ownership.new_owner").withStyle(TextFormatting.GREEN), ChatType.SYSTEM, Util.NIL_UUID);
        context.getSource().sendSuccess(new TranslationTextComponent("commands.teamprojecte.transfer_ownership.success", newOwner.getName()), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int kick(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null || !checkOwner(team, player))
            return 0;

        List<ServerPlayerEntity> kick = EntityArgument.getPlayers(context, "members").stream()
                .filter(p -> team.getMembers().contains(TeamProjectE.getPlayerUUID(p)))
                .collect(Collectors.toList());
        kick.forEach(p -> {
            team.removeMember(TeamProjectE.getPlayerUUID(p));
            p.sendMessage(new TranslationTextComponent("commands.teamprojecte.kicked").withStyle(TextFormatting.RED), ChatType.SYSTEM, Util.NIL_UUID);
        });

        if (!kick.isEmpty())
            context.getSource().sendSuccess(new TranslationTextComponent("commands.teamprojecte.kick.success", kick.size()), true);
        else
            context.getSource().sendFailure(new TranslationTextComponent("commands.teamprojecte.players_not_found"));

        return kick.size();
    }

    private static int members(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null)
            return 0;

        player.sendMessage(new TranslationTextComponent("commands.teamprojecte.members", getNames(team.getOwner(), team.getAll())), ChatType.SYSTEM, Util.NIL_UUID);
        return Command.SINGLE_SUCCESS;
    }

    private static ITextComponent getNames(UUID owner, List<UUID> uuids) {
        List<ITextComponent> components = new ArrayList<>();
        PlayerList playerList = ServerLifecycleHooks.getCurrentServer().getPlayerList();

        for (UUID uuid : uuids) {
            IFormattableTextComponent component;
            ServerPlayerEntity player = playerList.getPlayer(uuid);
            if (player != null)
                component = player.getName().copy()
                        .withStyle(TextFormatting.GREEN)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslationTextComponent("commands.teamprojecte.members.member_online"))));
            else if (UsernameCache.containsUUID(uuid))
                component = new StringTextComponent(UsernameCache.getLastKnownUsername(uuid))
                        .withStyle(TextFormatting.RED)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslationTextComponent("commands.teamprojecte.members.member_offline"))));
            else
                component = new StringTextComponent(uuid.toString())
                        .withStyle(TextFormatting.GRAY)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TranslationTextComponent("commands.teamprojecte.members.member_unknown"))));
            if (uuid.equals(owner))
                component.withStyle(TextFormatting.BOLD);
            components.add(component);
        }
        return TextComponentUtils.formatList(components, c -> c);
    }

    private static int leave(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null)
            return 0;
        team.removeMember(TeamProjectE.getPlayerUUID(player));
        return Command.SINGLE_SUCCESS;
    }

    private static int accept(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        UUID uuid = UUIDArgument.getUuid(context, "team");
        if (!INVITATIONS.get(TeamProjectE.getPlayerUUID(player)).contains(uuid)) {
            context.getSource().sendFailure(new TranslationTextComponent("commands.teamprojecte.invitation.not_found"));
            return -1;
        }

        INVITATIONS.remove(TeamProjectE.getPlayerUUID(player), uuid);

        TPTeam team = TPTeam.getTeam(uuid);
        if (team == null) {
            context.getSource().sendFailure(new TranslationTextComponent("commands.teamprojecte.team_not_found"));
            return -1;
        }
        TPTeam originalTeam = TPTeam.getTeamByMember(TeamProjectE.getPlayerUUID(player));
        if (originalTeam != null)
            team.addMemberWithKnowledge(originalTeam, player);
        else
            team.addMember(TeamProjectE.getPlayerUUID(player));

        context.getSource().sendSuccess(new TranslationTextComponent("commands.teamprojecte.invite.accepted").withStyle(TextFormatting.GREEN), false);
        ITextComponent component = new TranslationTextComponent("commands.teamprojecte.joined_team", player.getDisplayName()).withStyle(TextFormatting.GREEN);
        TeamProjectE.getAllOnline(team.getAll()).forEach(p -> p.sendMessage(component, Util.NIL_UUID));

        return Command.SINGLE_SUCCESS;
    }


    private static int decline(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        UUID uuid = UUIDArgument.getUuid(context, "team");
        if (!INVITATIONS.get(TeamProjectE.getPlayerUUID(player)).contains(uuid)) {
            context.getSource().sendFailure(new TranslationTextComponent("commands.teamprojecte.invitation.not_found"));
            return -1;
        }

        INVITATIONS.remove(TeamProjectE.getPlayerUUID(player), uuid);

        TPTeam team = TPTeam.getTeam(uuid);
        if (team == null) {
            context.getSource().sendFailure(new TranslationTextComponent("commands.teamprojecte.team_not_found"));
            return -1;
        }

        player.sendMessage(new TranslationTextComponent("commands.teamprojecte.invite.declined").withStyle(TextFormatting.RED), ChatType.SYSTEM, Util.NIL_UUID);
        TeamProjectE.getAllOnline(Collections.singletonList(team.getOwner())).forEach(p ->
                p.sendMessage(new TranslationTextComponent("commands.teamprojecte.invitation.declined", player.getDisplayName()), Util.NIL_UUID));

        return Command.SINGLE_SUCCESS;
    }

    private static CompletableFuture<Suggestions> createSuggestionsForInvitation(CommandContext<CommandSource> context, SuggestionsBuilder builder) throws CommandSyntaxException {
        PlayerEntity player = checkPlayer(context);
        return ISuggestionProvider.suggest(INVITATIONS.get(TeamProjectE.getPlayerUUID(player)).stream().map(UUID::toString), builder);
    }

    private static int invite(CommandContext<CommandSource> context) throws CommandSyntaxException {
        PlayerEntity player = checkPlayer(context);
        TPTeam team = TPTeam.getOrCreateTeam(TeamProjectE.getPlayerUUID(player));


        Collection<ServerPlayerEntity> players =
                EntityArgument.getPlayers(context, "players").stream()
                        .filter(p -> !team.getAll().contains(TeamProjectE.getPlayerUUID(p)))
                        .collect(Collectors.toList());

        ITextComponent component = new TranslationTextComponent("commands.teamprojecte.invitation",
                player.getDisplayName(),
                new TranslationTextComponent("commands.teamprojecte.invite.option.accept")
                        .withStyle(style -> style.applyFormat(TextFormatting.GREEN)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team_projecte accept " + team.getUUID()))),
                new TranslationTextComponent("commands.teamprojecte.invite.option.decline")
                        .withStyle(style -> style.applyFormat(TextFormatting.RED)
                                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/team_projecte decline " + team.getUUID())))
        );

        for (ServerPlayerEntity p : players) {
            INVITATIONS.put(TeamProjectE.getPlayerUUID(p), team.getUUID());
            p.sendMessage(component, ChatType.SYSTEM, Util.NIL_UUID);
        }
        if (!players.isEmpty())
            context.getSource().sendSuccess(new TranslationTextComponent("commands.teamprojecte.invite.success", players.size()), true);
        else
            context.getSource().sendFailure(new TranslationTextComponent("commands.teamprojecte.players_not_found"));
        return players.size();
    }

    private static ServerPlayerEntity checkPlayer(CommandContext<CommandSource> context) throws CommandSyntaxException {
        if (context.getSource().getEntity() instanceof ServerPlayerEntity)
            return (ServerPlayerEntity) context.getSource().getEntity();
        throw CommandSource.ERROR_NOT_PLAYER.create();
    }

    private static TPTeam checkInTeam(PlayerEntity player) {
        TPTeam team = TPTeam.getTeamByMember(TeamProjectE.getPlayerUUID(player));
        if (team == null || (team.getOwner().equals(TeamProjectE.getPlayerUUID(player)) && team.getMembers().isEmpty())) {
            player.sendMessage(new TranslationTextComponent("commands.teamprojecte.leave.not_in_team").withStyle(TextFormatting.RED), Util.NIL_UUID);
            return null;
        }
        return team;
    }

    private static boolean checkOwner(TPTeam team, ServerPlayerEntity player) {
        if (!TeamProjectE.getPlayerUUID(player).equals(team.getOwner())) {
            player.sendMessage(new TranslationTextComponent("commands.teamprojecte.not_owner").withStyle(TextFormatting.RED), ChatType.SYSTEM, Util.NIL_UUID);
            return false;
        }
        return true;
    }

    private static int queryShareEMC(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null)
            return 0;

        context.getSource().sendSuccess(new TranslationTextComponent("commands.teamprojecte.settings.query.sharing_emc." + team.isSharingEMC()), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setShareEMC(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null || !checkOwner(team, player))
            return 0;

        team.setShareEMC(BoolArgumentType.getBool(context, "value"));
        context.getSource().sendSuccess(new TranslationTextComponent("commands.teamprojecte.settings.set.sharing_emc." + team.isSharingEMC()), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int queryShareKnowledge(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null)
            return 0;

        context.getSource().sendSuccess(new TranslationTextComponent("commands.teamprojecte.settings.query.sharing_knowledge." + team.isSharingKnowledge()), true);

        return Command.SINGLE_SUCCESS;
    }

    private static int setShareKnowledge(CommandContext<CommandSource> context) throws CommandSyntaxException {
        ServerPlayerEntity player = checkPlayer(context);
        TPTeam team = checkInTeam(player);
        if (team == null || !checkOwner(team, player))
            return 0;

        team.setShareKnowledge(BoolArgumentType.getBool(context, "value"));
        context.getSource().sendSuccess(new TranslationTextComponent("commands.teamprojecte.settings.set.sharing_knowledge." + team.isSharingKnowledge()), true);

        return Command.SINGLE_SUCCESS;
    }

}