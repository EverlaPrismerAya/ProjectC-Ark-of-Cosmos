package net.everla.projectc.arkofcosmos;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder(ArkOfCosmos.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArkGameTests {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void attacks(GameTestHelper helper) {
        ArkCurvesCheck.main(new String[0]);
        geometry(helper);
        ServerPlayer player = new net.minecraftforge.common.util.FakePlayer(helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "ArkRegression")) {
            @Override public void teleportTo(double x, double y, double z) { setPos(x, y, z); }
        };
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 start = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 24, 1)));
        player.setPos(start);
        player.setYRot(0); player.setXRot(0); player.setNoGravity(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ArkOfCosmos.ARK_OF_COSMOS.get()));
        AttackFrame frame = AttackFrame.capture(player);
        helper.assertTrue(Math.abs(frame.forward().dot(frame.right())) < 1e-8, "Attack frame is not orthogonal");
        Vec3 aim = ArkCombat.aim(player);
        helper.assertTrue(Math.abs(aim.distanceTo(player.getBoundingBox().getCenter()) - 20) < 1e-6, "Idle throw target must be 20 blocks away");

        helper.assertTrue(ArkCombat.BASE_DAMAGE == 16 && Math.abs(ArkCombat.scaled(16, 1.35f) - 21.6f) < .001, "Damage scaling changed original multipliers");
        ItemStack weapon = player.getMainHandItem();
        helper.assertFalse(weapon.isDamageableItem(), "Weapon must have infinite durability");
        helper.assertTrue(Math.abs(1 + weapon.getAttributeModifiers(net.minecraft.world.entity.EquipmentSlot.MAINHAND).get(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).iterator().next().getAmount() - 16) < .001, "Mainhand damage panel must show 16");
        ArkItem.charge(weapon, 100);
        helper.assertFalse(weapon.getItem().isBarVisible(weapon), "State indicators must not use the vanilla durability bar");
        helper.assertTrue(weapon.getMaxDamage() == 0 && weapon.getItem() instanceof net.minecraft.world.item.SwordItem && weapon.isEnchantable(), "Sword must be enchantable without durability");
        weapon.getOrCreateTag().putInt("ArkParryCooldown", 170);
        helper.assertTrue(ArkItem.cooldown(weapon) == 170 && ArkItem.charge(weapon) == 16 && !weapon.getItem().isBarVisible(weapon), "Charge and cooldown must coexist without a durability bar");
        for (var enchantment : List.of(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS, net.minecraft.world.item.enchantment.Enchantments.SMITE, net.minecraft.world.item.enchantment.Enchantments.BANE_OF_ARTHROPODS, net.minecraft.world.item.enchantment.Enchantments.KNOCKBACK, net.minecraft.world.item.enchantment.Enchantments.FIRE_ASPECT, net.minecraft.world.item.enchantment.Enchantments.MOB_LOOTING, net.minecraft.world.item.enchantment.Enchantments.SWEEPING_EDGE, net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE))
            helper.assertTrue(enchantment.canEnchant(weapon), "Sword enchantment was rejected: " + enchantment);
        helper.assertFalse(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING.canEnchant(weapon) || net.minecraft.world.item.enchantment.Enchantments.MENDING.canEnchant(weapon), "Durability enchantments must be excluded");
        weapon.getOrCreateTag().putInt("ArkParryCooldown", 0); ArkItem.charge(weapon, 0);

        // Real server input path, including a press+release before the next server tick.
        int[] modes = {0, 1, 0, 1, 2};
        for (int expected : modes) {
            ArkCombat.controls(player).useCooldown = 0;
            ArkCombat.input(player, new ArkNetwork.Input(true, false, false));
            List<ArkAttackEntity> blades = attacks(player, ArkAttackEntity.Kind.BLADE);
            helper.assertTrue(blades.size() == 1 && blades.get(0).view().getInt("Mode") == expected, "Five-hit combo order is wrong");
            ArkCombat.input(player, new ArkNetwork.Input(false, false, false));
            clear(player);
        }
        helper.assertTrue(player.getMainHandItem().getTag().getInt("ArkCombo") == 0, "Combo did not wrap to zero");

        ArkCombat.Controls controls = ArkCombat.controls(player);
        controls.left = true;
        ArkAttackEntity thrown = ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLADE, 2, 0, 0,
                player.getBoundingBox().getCenter(), frame.forward(), frame);
        for (int i = 0; i < 6; i++) thrown.tick();
        helper.assertTrue(!attacks(player, ArkAttackEntity.Kind.CHAIN).isEmpty(), "Throw did not create constellation at 25%");
        ArkItem.charge(player.getMainHandItem(), 3);
        controls.left = false;
        thrown.tick();
        helper.assertTrue(ArkItem.charge(player.getMainHandItem()) == 3, "A snap without successful damage must not grant charge");
        helper.assertTrue(thrown.view().getList("Samples", 10).size() == 7, "Blade must publish all six simulation substeps");
        var path = thrown.view().getList("Samples", 10);
        Vec3 halfway = ArkAttackEntity.vector(path.getCompound(0), "Position").lerp(ArkAttackEntity.vector(path.getCompound(1), "Position"), .5);
        helper.assertTrue(thrown.renderPosition(1f / 12).distanceTo(halfway) < 1e-5, "Rendering must interpolate within original simulation substeps");
        float turn = (path.getCompound(0).getFloat("Rotation") + path.getCompound(1).getFloat("Rotation")) / 2;
        helper.assertTrue(Math.abs(thrown.renderRotation(1f / 12) - turn) < 1e-5, "Continuous rotation interpolation is incorrect");
        helper.assertTrue(thrown.view().getInt("Mode") == 3, "Release inside snap window did not snap");
        helper.assertTrue(attacks(player, ArkAttackEntity.Kind.BOLT).size() == 3, "Snap must emit three bolts");
        clear(player);

        controls.left = true;
        ArkAttackEntity early = ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLADE, 2, 0, 0,
                player.getBoundingBox().getCenter(), frame.forward(), frame);
        early.tick(); controls.left = false;
        for (int i = 0; i < 8; i++) early.tick();
        helper.assertTrue(early.view().getInt("Mode") == 2, "Early release incorrectly turned into a successful snap");
        clear(player);

        ArkAttackEntity needle = ArkAttackEntity.create(player, ArkAttackEntity.Kind.NEEDLE, 0, 0, 0,
                player.getBoundingBox().getCenter(), frame.forward().scale(2), frame);
        for (int i = 0; i < 9; i++) needle.tick();
        helper.assertFalse(needle.isRemoved(), "Needle expired before its 30th 60 Hz step");
        needle.tick();
        helper.assertTrue(needle.isRemoved(), "Needle did not expire after 30 original ticks");

        controls.parryCooldown = controls.useCooldown = 0;
        ArkCombat.input(player, new ArkNetwork.Input(false, true, false));
        helper.assertTrue(controls.invulnerableUntil - player.level().getGameTime() == 7, "Parry must last exactly seven MC ticks");
        LivingAttackEvent incoming = new LivingAttackEvent(player, player.damageSources().generic(), 3);
        MinecraftForge.EVENT_BUS.post(incoming);
        helper.assertTrue(incoming.isCanceled() && ArkItem.charge(player.getMainHandItem()) == 16, "Invulnerable hit did not fill charge");
        ArkItem.charge(player.getMainHandItem(), 4);
        incoming = new LivingAttackEvent(player, player.damageSources().generic(), 3);
        MinecraftForge.EVENT_BUS.post(incoming);
        helper.assertTrue(incoming.isCanceled() && ArkItem.charge(player.getMainHandItem()) == 4, "Parry must charge only once per window");
        controls.invulnerableUntil = player.level().getGameTime();
        incoming = new LivingAttackEvent(player, player.damageSources().generic(), 3);
        MinecraftForge.EVENT_BUS.post(incoming);
        helper.assertFalse(incoming.isCanceled(), "Expired parry still grants invulnerability");
        clear(player);

        // Targets beyond the throw range still steer the blade; friendly mobs must not attract it.
        Vec3 far = start.add(0, 0, 60);
        helper.getLevel().getChunk(BlockPos.containing(far));
        LivingEntity zombie = EntityType.ZOMBIE.create(helper.getLevel());
        zombie.setPos(far); zombie.setNoGravity(true);
        helper.getLevel().addFreshEntity(zombie);
        helper.assertTrue(ArkCombat.enemy(player, zombie), "Hostile target was rejected");
        Vec3 farAim = ArkCombat.aim(player);
        helper.assertTrue(Math.abs(farAim.distanceTo(player.getBoundingBox().getCenter()) - 47.5) < 1e-6, "Far target was not clamped to maximum reach");
        LivingEntity cow = EntityType.COW.create(helper.getLevel());
        cow.setPos(start.add(0, 0, 5)); helper.getLevel().addFreshEntity(cow);
        helper.assertFalse(ArkCombat.enemy(player, cow), "Friendly mob was treated as an enemy");
        net.minecraft.world.entity.monster.EnderMan enderman = EntityType.ENDERMAN.create(helper.getLevel());
        helper.assertFalse(ArkCombat.enemy(player, enderman), "Unprovoked neutral mob was treated as hostile");
        enderman.setTarget(player);
        helper.assertTrue(ArkCombat.enemy(player, enderman), "Neutral mob attacking player was not recognized");
        controls.left = true;
        thrown = ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLADE, 2, 0, 0,
                player.getBoundingBox().getCenter(), frame.forward(), frame);
        for (int i = 0; i < 17; i++) {
            thrown.tick();
            helper.assertTrue(thrown.position().distanceTo(player.getBoundingBox().getCenter()) <= 47.500001, "Thrown scissors exceeded max range");
        }
        clear(player); zombie.discard(); cow.discard();

        zombie = EntityType.ZOMBIE.create(helper.getLevel());
        zombie.setPos(start.add(0, 0, 100)); zombie.setNoGravity(true);
        helper.getLevel().getChunk(zombie.blockPosition()); helper.getLevel().addFreshEntity(zombie);
        controls.left = true;
        thrown = ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLADE, 2, 0, 1,
                player.getBoundingBox().getCenter(), frame.forward(), frame);
        for (int i = 0; i < 6; i++) thrown.tick();
        ArkItem.charge(player.getMainHandItem(), 3);
        controls.left = false; thrown.tick();
        helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "Confirmed unnormalized snap line did not hit beyond throw range");
        helper.assertTrue(ArkItem.charge(player.getMainHandItem()) == 8, "A damaging snap must grant five charges");
        thrown.tick();
        helper.assertTrue(ArkItem.charge(player.getMainHandItem()) == 8, "A snap must grant charge only once");
        clear(player); zombie.discard();

        ArkAttackEntity swirl = ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLADE, 1, 0, 0,
                player.getBoundingBox().getCenter(), frame.forward(), frame);
        for (int i = 0; i < 10 && !swirl.isRemoved(); i++) swirl.tick();
        helper.assertTrue(attacks(player, ArkAttackEntity.Kind.BOLT).size() == 6, "Swirl must emit six bolts at original substeps");
        clear(player);

        List<LivingEntity> targets = new java.util.ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            LivingEntity mob = EntityType.ZOMBIE.create(helper.getLevel());
            mob.setPos(start.add(0, 0, i)); helper.getLevel().addFreshEntity(mob); targets.add(mob);
        }
        needle = ArkAttackEntity.create(player, ArkAttackEntity.Kind.NEEDLE, 0, 0, 1,
                player.getBoundingBox().getCenter(), frame.forward().scale(.5), frame);
        needle.tick();
        helper.assertTrue(needle.isRemoved() && targets.stream().allMatch(mob -> mob.getHealth() < mob.getMaxHealth()), "Needle must damage and penetrate exactly three targets");
        targets.forEach(LivingEntity::discard); clear(player);

        // Auto-target exclusions must not prevent real geometry hits on passive or neutral mobs.
        for (EntityType<? extends LivingEntity> type : List.of(EntityType.COW, EntityType.WOLF, EntityType.ENDERMAN)) {
            LivingEntity victim = type.create(helper.getLevel());
            victim.setPos(start.add(0, 0, 5)); victim.setNoGravity(true);
            helper.getLevel().addFreshEntity(victim);
            helper.assertTrue(ArkCombat.canHit(player, victim), "Damage predicate rejected " + type);
            ArkAttackEntity strike = ArkAttackEntity.create(player, ArkAttackEntity.Kind.BOLT, 0, 0, 1,
                    victim.getBoundingBox().getCenter().subtract(frame.forward().scale(.01)), frame.forward().scale(.01), frame);
            strike.tick();
            helper.assertTrue(victim.getHealth() < victim.getMaxHealth(), "Direct attack failed against " + type);
            victim.discard(); clear(player);
        }
        // Parry has the original line reach and charges immediately on direct contact.
        LivingEntity parryVictim = EntityType.COW.create(helper.getLevel());
        parryVictim.setPos(start.add(0, 0, 10)); helper.getLevel().addFreshEntity(parryVictim);
        controls.parried = false; controls.parryStack = player.getMainHandItem(); ArkItem.charge(weapon, 0);
        ArkAttackEntity parry = ArkAttackEntity.create(player, ArkAttackEntity.Kind.PARRY, 0, 0, ArkCombat.BASE_DAMAGE,
                player.getBoundingBox().getCenter(), Vec3.ZERO, frame);
        parry.tick();
        helper.assertTrue(parryVictim.getHealth() < parryVictim.getMaxHealth() && ArkItem.charge(weapon) == 16,
                "Parry blade must hit at original reach and fill charge immediately");
        parryVictim.discard(); clear(player);
        LivingEntity outside = EntityType.COW.create(helper.getLevel());
        outside.setPos(start.add(0, 0, 20)); helper.getLevel().addFreshEntity(outside);
        controls.parried = false; ArkItem.charge(weapon, 0);
        parry = ArkAttackEntity.create(player, ArkAttackEntity.Kind.PARRY, 0, 0, ArkCombat.BASE_DAMAGE,
                player.getBoundingBox().getCenter(), Vec3.ZERO, frame);
        for (int i = 0; i < 6; i++) parry.tick();
        helper.assertTrue(outside.getHealth() == outside.getMaxHealth() && ArkItem.charge(weapon) == 0, "Parry reached beyond original geometry");
        outside.discard(); clear(player);

        // Lock transition is filtered at original simulation rate, rather than teleporting its aim.
        controls.left = true;
        thrown = ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLADE, 2, 0, 0,
                player.getBoundingBox().getCenter(), frame.forward(), frame);
        thrown.tick();
        Vec3 oldAim = ArkAttackEntity.vector(thrown.view(), "Aim");
        LivingEntity lockTarget = EntityType.ZOMBIE.create(helper.getLevel());
        lockTarget.setPos(start.add(0, 0, 40)); helper.getLevel().addFreshEntity(lockTarget);
        Vec3 desired = ArkCombat.aim(player);
        thrown.tick();
        Vec3 filtered = ArkAttackEntity.vector(thrown.view(), "Aim");
        helper.assertTrue(filtered.distanceTo(oldAim) > .1 && filtered.distanceTo(desired) > 1 && filtered.distanceTo(oldAim) < desired.distanceTo(oldAim), "Lock transition did not smooth its aim");
        lockTarget.discard(); clear(player);

        // The burst is Alt+Use; ordinary Use must not consume charge as a burst.
        controls.right = false; controls.useCooldown = 0; controls.parryCooldown = 0;
        ArkItem.charge(player.getMainHandItem(), 5);
        ArkCombat.input(player, new ArkNetwork.Input(false, true, true));
        helper.assertTrue(ArkItem.charge(player.getMainHandItem()) == 0 && attacks(player, ArkAttackEntity.Kind.BLAST).size() == 1,
                "Alt+Use did not consume charge and create blast");
        clear(player);

        // A clear destination allows crossing a wall; a blocked destination must not.
        dash(helper, player, start, frame, true);
        dash(helper, player, start, frame, false);
        clear(player);
        player.discard();
        helper.succeed();
    }

    private static void dash(GameTestHelper helper, ServerPlayer player, Vec3 start, AttackFrame frame, boolean clearEnd) {
        player.setPos(start);
        player.setShiftKeyDown(true);
        player.setOnGround(true);
        ArkCombat.Controls controls = ArkCombat.controls(player);
        controls.alt = controls.right = true;
        Vec3 endpoint = start.add(0, 0, 45.625);
        helper.getLevel().getChunk(BlockPos.containing(endpoint));
        BlockPos wall = BlockPos.containing(start.add(0, 0, 15));
        BlockPos end = BlockPos.containing(endpoint);
        // Clear old generated terrain along the route before placing the test wall.
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(start).offset(-1, 0, -1), end.offset(1, 2, 1)))
            helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlockAndUpdate(wall.above(y), Blocks.STONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(end.above(y), (clearEnd ? Blocks.AIR : Blocks.STONE).defaultBlockState());
        }
        if (clearEnd) {
            AABB destination = player.getBoundingBox().move(endpoint.subtract(player.position())).deflate(1e-5);
            // The game test region can contain old generated structures across repeated runs.
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(destination.minX, destination.minY, destination.minZ),
                    BlockPos.containing(destination.maxX, destination.maxY, destination.maxZ)))
                helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            helper.assertFalse(helper.getLevel().getBlockCollisions(player, destination).iterator().hasNext(), "Test destination is not clear: " + destination);
        }
        ArkAttackEntity blast = ArkAttackEntity.create(player, ArkAttackEntity.Kind.BLAST, 0, 5, 0,
                player.getBoundingBox().getCenter().add(frame.forward().scale(90 * ArkCombat.PIXEL)), Vec3.ZERO, frame);
        LivingEntity obstruction = EntityType.SHULKER.create(helper.getLevel());
        obstruction.setPos(start.add(0, 0, 4));
        helper.getLevel().addFreshEntity(obstruction);
        for (int i = 0; i < 24 && !blast.isRemoved(); i++) {
            player.setDeltaMovement(new Vec3(1, 0, -1)); // External push must not redirect the dash.
            blast.tick();
        }
        obstruction.discard();
        player.setShiftKeyDown(false);
        helper.assertTrue(clearEnd ? player.getZ() > wall.getZ() + 1 : player.getZ() > start.z + 10 && player.getZ() < wall.getZ(),
                (clearEnd ? "Clear endpoint did not allow dash through intermediate wall" : "Blocked endpoint allowed dash through wall")
                        + ": player=" + player.position() + " wall=" + wall + " endpoint=" + endpoint);
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlockAndUpdate(wall.above(y), Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(end.above(y), Blocks.AIR.defaultBlockState());
        }
    }

    private static void geometry(GameTestHelper helper) {
        AttackFrame frame = new AttackFrame(new Vec3(1, 0, 0), new Vec3(0, 0, 1));
        helper.assertTrue(ArkGeometry.line(new AABB(2, -.1, -.1, 3, .1, .1), Vec3.ZERO, new Vec3(5, 0, 0), .5, frame), "Line missed an intersecting target");
        helper.assertFalse(ArkGeometry.line(new AABB(2, 1, 1, 3, 2, 2), Vec3.ZERO, new Vec3(5, 0, 0), .5, frame), "Line hit outside its volume");
        Vec3 reverse = ArkGeometry.turn(new Vec3(1, 0, 0), new Vec3(-1, 0, 0), .1, frame.normal());
        helper.assertTrue(Double.isFinite(reverse.x) && Math.abs(reverse.length() - .995) < 1e-8, "Opposite homing direction is unstable");
    }

    private static List<ArkAttackEntity> attacks(ServerPlayer player, ArkAttackEntity.Kind kind) {
        return player.level().getEntitiesOfClass(ArkAttackEntity.class, player.getBoundingBox().inflate(128), a -> a.ownerId() == player.getId() && a.kind() == kind);
    }
    private static void clear(ServerPlayer player) {
        player.level().getEntitiesOfClass(ArkAttackEntity.class, player.getBoundingBox().inflate(128), a -> a.ownerId() == player.getId()).forEach(ArkAttackEntity::discard);
    }
}
