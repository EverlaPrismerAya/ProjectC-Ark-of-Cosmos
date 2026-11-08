package net.everla.projectc.arkofcosmos.revengeance;

import net.everla.projectc.arkofcosmos.ArkOfCosmos;
import net.everla.projectc.arkofcosmos.revengeance.RevengeanceModAttributes;
import net.everla.projectc.arkofcosmos.revengeance.RevengeanceModMobEffects;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = ArkOfCosmos.MOD_ID, value = Dist.CLIENT)
public class RageBarsOverlay {
	private static final ResourceLocation RAGE_BAR_BASE = ResourceLocation.tryParse(
			ArkOfCosmos.MOD_ID + ":textures/screens/rage_bar_base.png");
	private static final ResourceLocation RAGE_BAR_FILL = ResourceLocation.tryParse(
			ArkOfCosmos.MOD_ID + ":textures/screens/rage_bar_fill.png");
	
	private static final ResourceLocation[] RAGE_FULL_ANIMATION = new ResourceLocation[10];
	static {
		for (int i = 0; i < 10; i++) {
			RAGE_FULL_ANIMATION[i] = ResourceLocation.tryParse(
					ArkOfCosmos.MOD_ID + ":textures/screens/rage_full_animation_" + (i + 1) + ".png");
		}
	}
	
	private static final ResourceLocation[] ADRENALINE_FULL_ANIMATION = new ResourceLocation[10];
	static {
		for (int i = 0; i < 10; i++) {
			ADRENALINE_FULL_ANIMATION[i] = ResourceLocation.tryParse(
					ArkOfCosmos.MOD_ID + ":textures/screens/adrenaline_full_animation_" + (i + 1) + ".png");
		}
	}

	private static final ResourceLocation ADRENALINE_BAR_BASE = ResourceLocation.tryParse(
			ArkOfCosmos.MOD_ID + ":textures/screens/adrenaline_bar_base.png");
	private static final ResourceLocation ADRENALINE_BAR_FILL = ResourceLocation.tryParse(
			ArkOfCosmos.MOD_ID + ":textures/screens/adrenaline_bar_fill.png");
	private static final ResourceLocation ADRENALINE_BAR_FULL = ResourceLocation.tryParse(
			ArkOfCosmos.MOD_ID + ":textures/screens/adrenaline_bar_full.png");

	private static boolean rageWasFull = false;
	private static boolean isPlayingRageAnimation = false;
	private static int rageAnimationFrame = 0;
	private static long rageAnimationStartTime = 0;
	private static final int RAGE_ANIMATION_FRAME_DURATION = 1;
	
	private static boolean adrenalineWasFull = false;
	private static boolean isPlayingAdrenalineAnimation = false;
	private static int adrenalineAnimationFrame = 0;
	private static long adrenalineAnimationStartTime = 0;
	private static final int ADRENALINE_ANIMATION_FRAME_DURATION = 1;
	
	// 预计算的抖动值数组，提高性能
	private static final int SHAKE_PRECOMPUTE_SIZE = 100;
	private static final int[] precomputedRageShakeX = new int[SHAKE_PRECOMPUTE_SIZE];
	private static final int[] precomputedRageShakeY = new int[SHAKE_PRECOMPUTE_SIZE];
	private static final int[] precomputedAdrenalineShakeX = new int[SHAKE_PRECOMPUTE_SIZE];
	private static final int[] precomputedAdrenalineShakeY = new int[SHAKE_PRECOMPUTE_SIZE];
	private static int currentShakeIndex = 0;
	private static long lastShakeIndexUpdate = 0;
	
	private static final int SHAKE_UPDATE_INTERVAL = 8; // 每8ms更新一次抖动索引，提高抖动频率
	
	static {
		// 预计算所有抖动值
		precomputeShakeValues();
	}
	
	// 缓存动画计算结果
	private static long lastAnimationUpdateTime = 0;
	private static final int ANIMATION_UPDATE_INTERVAL = 16; // 动画帧更新间隔
	
	// 预计算的动画状态缓存
	private static boolean cachedRageAnimationPlaying = false;
	private static boolean cachedAdrenalineAnimationPlaying = false;
	private static int cachedRageAnimationFrame = 0;
	private static int cachedAdrenalineAnimationFrame = 0;

	@SubscribeEvent(priority = EventPriority.NORMAL)
	public static void onPreGui(RenderGuiEvent.Pre event) {
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.options.hideGui || player.isSpectator()) return;

		AttributeMap attributes = player.getAttributes();
		double worldRage = attributes.getValue(RevengeanceModAttributes.RAGE_LEVEL.get());
		double worldAdren = attributes.getValue(RevengeanceModAttributes.ADRENALINE_LEVEL.get());

		double maxRage = net.everla.projectc.arkofcosmos.ProjectCCommonConfig.boost("rage","required_charge");
		double maxAdrenaline = net.everla.projectc.arkofcosmos.ProjectCCommonConfig.boost("adrenaline","required_charge");

		double ragePct = clamp(worldRage / maxRage, 0.0, 1.0);
		double adrenPct = clamp(worldAdren / maxAdrenaline, 0.0, 1.0);

		int screenH = event.getGuiGraphics().guiHeight();

		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.blendFuncSeparate(
				GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
				GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

		// 检查怒气是否刚刚达到满值，触发动画
		boolean rageIsFull = ragePct >= 1.0;
		if (rageIsFull && !rageWasFull && !isPlayingRageAnimation) {
			// 怒气刚刚满，开始播放动画
			isPlayingRageAnimation = true;
			rageAnimationFrame = 0;
			rageAnimationStartTime = System.currentTimeMillis();
		}
		rageWasFull = rageIsFull;

		// 检查肾上腺素是否刚刚达到满值，触发动画
		boolean adrenalineIsFull = adrenPct >= 1.0;
		if (adrenalineIsFull && !adrenalineWasFull && !isPlayingAdrenalineAnimation) {
			// 肾上腺素刚刚满，开始播放动画
			isPlayingAdrenalineAnimation = true;
			adrenalineAnimationFrame = 0;
			adrenalineAnimationStartTime = System.currentTimeMillis();
		}
		adrenalineWasFull = adrenalineIsFull;

		// 更新动画帧 - 优化为只在需要时计算
		long currentTime = System.currentTimeMillis();
		updateAnimationStates(currentTime);

		// —— Rage Bar ——
		int rageBarW = 76, rageBarH = 19;
		int x_rage = -7, y_rage = screenH - 20;
		
		// 检查是否有怒气效果激活，如果有则添加抖动
		boolean hasRageEffect = player.hasEffect(RevengeanceModMobEffects.RAGE_EFFECT.get());
		
		// 检查是否有肾上腺素效果激活
		boolean hasAdrenalineEffect = player.hasEffect(RevengeanceModMobEffects.ADRENALINE_EFFECT.get());
		
		// 如果任何一个效果激活，就更新抖动索引
		if (hasRageEffect || hasAdrenalineEffect) {
			updateShakeIndex(currentTime);
		}
		
		if (hasRageEffect) {
			// 使用预计算的抖动值
			x_rage += precomputedRageShakeX[currentShakeIndex];
			y_rage += precomputedRageShakeY[currentShakeIndex];
		}
		
		event.getGuiGraphics().blit(
				RAGE_BAR_BASE, x_rage, y_rage,
				0, 0, rageBarW, rageBarH, rageBarW, rageBarH);

		// fill 源图有效区间 [18,58)
		int rageFillStart = 18, rageFillEnd = 58;
		int rageFillWidth = rageFillEnd - rageFillStart;
		int filledRage = (int) Math.floor(ragePct * rageFillWidth);
		if (filledRage > 0) {
			event.getGuiGraphics().blit(
					RAGE_BAR_FILL,
					x_rage, y_rage,                  // 目的地起点保持与 base 一致（包含抖动偏移）
					0, 0,               // 源图裁剪起点
					rageFillStart + filledRage, rageBarH,           // 裁剪 & 绘制宽高
					rageBarW, rageBarH);            // 纹理整体宽高
		}

		// —— Rage Full Animation ——
		if (cachedRageAnimationPlaying && cachedRageAnimationFrame < 10) {
			event.getGuiGraphics().blit(
					RAGE_FULL_ANIMATION[cachedRageAnimationFrame],
					x_rage, y_rage,    // 使用相同的坐标（包含抖动偏移）
					0, 0, rageBarW, rageBarH, rageBarW, rageBarH);
		}

		// —— Adrenaline Bar ——
		int adrenBarW = 77, adrenBarH = 34;
		int x_adren = 42, y_adren = screenH - 26;
		
		if (hasAdrenalineEffect) {
			// 使用预计算的抖动值（肾上腺素使用不同的抖动模式）
			x_adren += precomputedAdrenalineShakeX[currentShakeIndex];
			y_adren += precomputedAdrenalineShakeY[currentShakeIndex];
		}
		// 始终先绘制 base
		event.getGuiGraphics().blit(
				ADRENALINE_BAR_BASE, x_adren, y_adren,
				0, 0, adrenBarW, adrenBarH, adrenBarW, adrenBarH);

		// fill 源图有效区间 [23,63)
		int adrenFillStart = 23, adrenFillEnd = 63;
		int adrenFillWidth = adrenFillEnd - adrenFillStart;
		int filledAdren = (int) Math.floor(adrenPct * adrenFillWidth);
		if (filledAdren > 0 && adrenPct < 1.0) {
			event.getGuiGraphics().blit(
					ADRENALINE_BAR_FILL,
					x_adren, y_adren,
					0, 0,
					adrenFillStart + filledAdren, adrenBarH,
					adrenBarW, adrenBarH);
		}

		// 100% 时额外叠加 full
		if (adrenPct >= 1.0) {
			event.getGuiGraphics().blit(
					ADRENALINE_BAR_FULL, x_adren, y_adren,
					0, 0, adrenBarW, adrenBarH, adrenBarW, adrenBarH);
		}

		// —— Adrenaline Full Animation ——
		if (cachedAdrenalineAnimationPlaying && cachedAdrenalineAnimationFrame < 10) {
			event.getGuiGraphics().blit(
					ADRENALINE_FULL_ANIMATION[cachedAdrenalineAnimationFrame],
					x_adren, y_adren,    // 使用相同的坐标（包含抖动偏移）
					0, 0, adrenBarW, adrenBarH, adrenBarW, adrenBarH);
		}

		RenderSystem.depthMask(true);
		RenderSystem.defaultBlendFunc();
		RenderSystem.enableDepthTest();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
	}

	/**
	 * 预计算抖动值，避免运行时三角函数计算
	 */
	private static void precomputeShakeValues() {
		double rageShakeIntensity = 1.1;
		double adrenalineShakeIntensity = 1.1;
		
		for (int i = 0; i < SHAKE_PRECOMPUTE_SIZE; i++) {
			// 为不同的抖动效果使用不同的频率和相位
			double ragePhase = (i * 2.0 * Math.PI) / SHAKE_PRECOMPUTE_SIZE;
			double adrenalinePhase = (i * 2.0 * Math.PI) / SHAKE_PRECOMPUTE_SIZE;
			
			// 怒气抖动使用原始频率模式
			precomputedRageShakeX[i] = (int) (Math.sin(ragePhase * 0.05 * 50) * rageShakeIntensity);
			precomputedRageShakeY[i] = (int) (Math.cos(ragePhase * 0.08 * 50) * rageShakeIntensity);
			
			// 肾上腺素抖动使用不同频率
			precomputedAdrenalineShakeX[i] = (int) (Math.sin(adrenalinePhase * 0.07 * 50) * adrenalineShakeIntensity);
			precomputedAdrenalineShakeY[i] = (int) (Math.cos(adrenalinePhase * 0.06 * 50) * adrenalineShakeIntensity);
		}
	}
	
	/**
	 * 更新抖动索引，控制抖动效果的变化
	 */
	private static void updateShakeIndex(long currentTime) {
		if (currentTime - lastShakeIndexUpdate >= SHAKE_UPDATE_INTERVAL) {
			// 每次更新跳跃更多索引，增加抖动的随机性和频率
			currentShakeIndex = (currentShakeIndex + 2) % SHAKE_PRECOMPUTE_SIZE;
			lastShakeIndexUpdate = currentTime;
		}
	}

	/**
	 * 优化的动画状态更新方法，减少重复计算
	 */
	private static void updateAnimationStates(long currentTime) {
		if (currentTime - lastAnimationUpdateTime >= ANIMATION_UPDATE_INTERVAL) {
			// 更新怒气动画
			if (isPlayingRageAnimation) {
				long elapsedTicks = (currentTime - rageAnimationStartTime) / 50;
				int targetFrame = (int) (elapsedTicks / RAGE_ANIMATION_FRAME_DURATION);
				
				if (targetFrame >= 10) {
					isPlayingRageAnimation = false;
					cachedRageAnimationPlaying = false;
					rageAnimationFrame = 0;
					cachedRageAnimationFrame = 0;
				} else {
					rageAnimationFrame = targetFrame;
					cachedRageAnimationFrame = targetFrame;
					cachedRageAnimationPlaying = true;
				}
			} else {
				cachedRageAnimationPlaying = false;
			}
			
			// 更新肾上腺素动画
			if (isPlayingAdrenalineAnimation) {
				long elapsedTicks = (currentTime - adrenalineAnimationStartTime) / 50;
				int targetFrame = (int) (elapsedTicks / ADRENALINE_ANIMATION_FRAME_DURATION);
				
				if (targetFrame >= 10) {
					isPlayingAdrenalineAnimation = false;
					cachedAdrenalineAnimationPlaying = false;
					adrenalineAnimationFrame = 0;
					cachedAdrenalineAnimationFrame = 0;
				} else {
					adrenalineAnimationFrame = targetFrame;
					cachedAdrenalineAnimationFrame = targetFrame;
					cachedAdrenalineAnimationPlaying = true;
				}
			} else {
				cachedAdrenalineAnimationPlaying = false;
			}
			
			lastAnimationUpdateTime = currentTime;
		}
	}

	private static double clamp(double v, double min, double max) {
		return v < min ? min : (v > max ? max : v);
	}
}
