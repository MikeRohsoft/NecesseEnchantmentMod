package enchantmentmod.items;

import enchantmentmod.Config;
import enchantmentmod.items.network.PacketPlayerOpenedLuckyBoxRequest;
import necesse.engine.localization.Localization;
import necesse.engine.localization.message.GameMessage;
import necesse.engine.network.Packet;
import necesse.engine.network.server.ServerClient;
import necesse.engine.registries.ItemRegistry;
import necesse.engine.util.GameBlackboard;
import necesse.entity.mobs.PlayerMob;
import necesse.gfx.gameTooltips.ListGameTooltips;
import necesse.gfx.gameTooltips.StringTooltips;
import necesse.inventory.InventoryItem;
import necesse.inventory.container.Container;
import necesse.inventory.container.ContainerActionResult;
import necesse.inventory.container.slots.ContainerSlot;
import necesse.inventory.enchants.EquipmentItemEnchant;
import necesse.inventory.enchants.ItemEnchantment;
import necesse.inventory.item.miscItem.EnchantingScrollItem;
import necesse.level.maps.Level;
import java.util.Random;
import java.util.function.Supplier;

public class LuckyBoxItem extends EnchantingScrollItem {
    public LuckyBoxItem() {
        super();
    }
    @Override
    public ListGameTooltips getTooltips(InventoryItem item, PlayerMob perspective, GameBlackboard blackboard) {
        ListGameTooltips tooltips = new ListGameTooltips();
        tooltips.add(new StringTooltips(this.getDisplayName(item), this.getRarityColor(item)));
        tooltips.add(Localization.translate("itemtooltip", "rclickinvopentip"));
        return tooltips;
    }

    @Override
    public EnchantScrollType getType(ItemEnchantment enchantment) {
        return super.getType(enchantment);
    }

    @Override
    public Supplier<ContainerActionResult> getInventoryRightClickAction(Container container, InventoryItem item, int slotIndex, ContainerSlot slot) {
        return () -> {
            if (slot.getInventory() != container.getClient().playerMob.getInv().main || !container.getClient().isServerClient()) {
                return new ContainerActionResult(-1390943614);
            }

            ServerClient client = container.getClient().getServerClient();
            Random rand = new Random();
            Config cfg = Config.getInstance();
            InventoryItem ivItemCandidate = new InventoryItem(
                    "enchantmentshard",
                    rand.nextInt((cfg.getLuckyBoxMax() - cfg.getLuckyBoxMin()) + 1) + cfg.getLuckyBoxMin()
            );

            Packet itemContent = InventoryItem.getContentPacket(ivItemCandidate);
            client.sendPacket(new PacketPlayerOpenedLuckyBoxRequest(itemContent.getPacketData()));

            client.playerMob.getInv().main.removeItems(
                container.client.playerMob.getLevel(),
                container.client.playerMob,
                ItemRegistry.getItem("luckybox"),
                1,
                "buy"
            );

            client.playerMob.getInv().addItem(ivItemCandidate, true, "itempickup", null);
            return new ContainerActionResult(-1390943614);
        };
    }
    @Override
    public void setEnchantment(InventoryItem item, int enchantment) {
       // Empty
    }
    @Override
    public ItemEnchantment getEnchantment(InventoryItem item) {
        return EquipmentItemEnchant.noEnchant;
    }
    @Override
    public boolean canCombineItem(Level level, PlayerMob player, InventoryItem me, InventoryItem him, String purpose) {
        return false;
    }
    @Override
    public float getBrokerValue(InventoryItem item) {
        return super.getBrokerValue(item);
    }
    @Override
    public GameMessage getLocalization(InventoryItem item) {
        return super.getLocalization(item);
    }
}
