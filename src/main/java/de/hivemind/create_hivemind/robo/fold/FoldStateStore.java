package de.hivemind.create_hivemind.robo.fold;

import de.theidler.create_mobile_packages.robo.VirtualRobo;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 按逻辑蜂记录折跃状态。弱引用键：运输蜂对象被回收后条目自动消失，无需清理。
 *
 * <p>所有调用都发生在服务端主线程（{@code LevelTickEvent.Pre} → {@code RoboManager.tick}），
 * 因此不需要额外同步。若将来出现跨线程访问，需要换成同步包装。</p>
 */
public final class FoldStateStore {

    private static final Map<VirtualRobo, FoldState> STATES = new WeakHashMap<>();

    public static FoldState get(VirtualRobo robo) {
        return STATES.computeIfAbsent(robo, r -> new FoldState());
    }

    public static void forget(VirtualRobo robo) {
        STATES.remove(robo);
    }

    public static int trackedCount() {
        return STATES.size();
    }

    private FoldStateStore() {
    }
}
