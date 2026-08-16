package cc.minota.chateau;

import cc.minota.chateau.client.ChateauClient;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(Chateau.MOD_ID)
public class Chateau {
    public static final String MOD_ID = "chateau";
    public static final Logger LOGGER = LoggerFactory.getLogger("chateau");

    public Chateau() {
        // Everything this mod does is client side, so on a dedicated server it just sits there.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ChateauClient::init);
    }
}
