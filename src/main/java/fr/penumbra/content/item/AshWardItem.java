package fr.penumbra.content.item;

import fr.penumbra.PenumbraMod;
import fr.penumbra.entity.absent.AbsentEntity;
import fr.penumbra.entity.contrefait.ContrefaitEntity;
import fr.penumbra.entity.originel.OriginelEntity;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.List;
import java.util.function.Consumer;

/**
 * Sort consommable de bannissement. Son filtre par namespace repousse toutes
 * les manifestations hostiles de Penumbra, sans toucher aux monstres vanilla,
 * aux joueurs, aux animaux ni aux cadres photo.
 */
public final class AshWardItem extends Item {

    public static final double RADIUS = 16.0;
    private static final int EMPTY_COOLDOWN_TICKS = 20;
    private static final int SUCCESS_COOLDOWN_TICKS = 60;

    public AshWardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        List<Monster> targets = serverLevel.getEntities(
                EntityTypeTest.forClass(Monster.class),
                player.getBoundingBox().inflate(RADIUS),
                monster -> isBanishableFor(serverPlayer, monster)
                        && AshWardPolicy.isWithinRadius(
                                monster.distanceToSqr(player), RADIUS));
        if (targets.isEmpty()) {
            player.getCooldowns().addCooldown(stack, EMPTY_COOLDOWN_TICKS);
            serverPlayer.sendOverlayMessage(
                    Component.translatable("message.penumbra.ash_ward.empty"));
            return InteractionResult.SUCCESS_SERVER;
        }

        int banished = 0;
        for (Monster target : targets) {
            if (target instanceof AbsentEntity absent
                    && !absent.beginAshBanishment(serverPlayer)) {
                continue;
            }
            if (target instanceof AbsentEntity absent && absent.isFalseCompanion()) {
                serverLevel.sendParticles(serverPlayer, PenumbraParticles.ASH_MOTE,
                        false, false,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5,
                        target.getZ(), 24, 0.35, 0.55, 0.35, 0.035);
            } else {
                serverLevel.sendParticles(PenumbraParticles.ASH_MOTE,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5,
                        target.getZ(), 24, 0.35, 0.55, 0.35, 0.035);
            }
            if (!(target instanceof AbsentEntity)) {
                target.discard();
            }
            banished++;
        }
        if (banished == 0) {
            player.getCooldowns().addCooldown(stack, EMPTY_COOLDOWN_TICKS);
            serverPlayer.sendOverlayMessage(
                    Component.translatable("message.penumbra.ash_ward.empty"));
            return InteractionResult.SUCCESS_SERVER;
        }
        serverLevel.sendParticles(PenumbraParticles.ASH_MOTE,
                player.getX(), player.getY() + 1.0, player.getZ(),
                52, 1.2, 0.7, 1.2, 0.08);
        PenumbraNetworking.sendSoundPriority(serverPlayer, PenumbraSounds.RITUAL_RELEASE,
                player.position(), 1.0f, 1.18f);
        serverPlayer.sendOverlayMessage(Component.translatable(
                "message.penumbra.ash_ward.success", banished));
        player.getCooldowns().addCooldown(stack, SUCCESS_COOLDOWN_TICKS);
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    static boolean isBanishable(Monster monster) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(monster.getType());
        return id != null && PenumbraMod.MOD_ID.equals(id.getNamespace());
    }

    private static boolean isBanishableFor(ServerPlayer caster, Monster monster) {
        if (!isBanishable(monster)) {
            return false;
        }
        if (monster instanceof OriginelEntity originel) {
            return AshWardPolicy.canBanishFor(true, originel.isOwnedBy(caster));
        }
        if (monster instanceof ContrefaitEntity contrefait) {
            return AshWardPolicy.canBanishFor(true, contrefait.isOwnedBy(caster));
        }
        if (monster instanceof AbsentEntity absent) {
            return AshWardPolicy.canBanishFor(true, absent.isOwnedBy(caster));
        }
        return AshWardPolicy.canBanishFor(false, false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> consumer,
                                TooltipFlag flag) {
        consumer.accept(Component.translatable("item.penumbra.ash_ward.desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        consumer.accept(Component.translatable("item.penumbra.ash_ward.controls")
                .withStyle(ChatFormatting.GRAY));
    }
}
