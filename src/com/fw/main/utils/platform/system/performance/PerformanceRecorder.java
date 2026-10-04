package com.fw.main.utils.platform.system.performance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import com.fw.internal.sys.base.view.AccessConsole;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.utils.platform.system.console.Console;
import com.sun.management.OperatingSystemMXBean;
import java.util.*;

public class PerformanceRecorder {

    public enum CaptureMode {
        EVERY_FRAME(1),
        HALF_SECOND(30),
        EVERY_SECOND(60),
        DO_NOT(60);

        private final int interval;
        CaptureMode(int interval) { this.interval = interval; }
        public int getInterval() { return interval; }
    }

    private static class PrimitiveLongList {
        private long[] array = new long[2048];
        private int size = 0;

        public void add(long val) {
            if (size == array.length) {
                long[] newArr = new long[array.length * 2];
                System.arraycopy(array, 0, newArr, 0, array.length);
                array = newArr;
            }
            array[size++] = val;
        }

        public int size() {
            return size;
        }

        public Long[] toObjectArray() {
            Long[] result = new Long[size];
            for (int i = 0; i < size; i++) {
                result[i] = array[i];
            }
            return result;
        }

        public void writeCsv(StringBuilder sb) {
            for (int i = 0; i < size; i++) {
                sb.append(array[i]);
                if (i < size - 1) sb.append(",");
            }
        }
    }

    private final BaseWorkTimeProvider baseProvider;
    private final CaptureMode mode;
    private final Object lock = new Object();
    private final Map<String, PrimitiveLongList> recordMap = new LinkedHashMap<>();
    private final Map<Integer, String> phaseMap = new TreeMap<>();

    private int frameCounter = 0;
    private long lastTimestampNs = 0;

    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memBean;
    private final List<GarbageCollectorMXBean> gcBeans;

    public PerformanceRecorder(BaseWorkTimeProvider baseProvider, AccessConsole accessConsole, CaptureMode mode) {
        if (mode == CaptureMode.DO_NOT) {
            accessConsole.getConsole().addLog(Console.LogType.ERROR,"DO_NOT option bypassed for PerformanceRecorder. Overriding with EVERY_SECOND recording.");
        }
        this.baseProvider = baseProvider;
        this.mode = (mode != null) ? mode : CaptureMode.EVERY_FRAME;

        synchronized (lock) {
            recordMap.put("CPU_USAGE_PERCENT", new PrimitiveLongList());
            recordMap.put("HEAP_USED_BYTES", new PrimitiveLongList());
            recordMap.put("NON_HEAP_USED_BYTES", new PrimitiveLongList());
            recordMap.put("GC_TIME_MS", new PrimitiveLongList());
            recordMap.put("FRAME_TIME_NS", new PrimitiveLongList());
            recordMap.put("BASE_CPU_WORK_NS", new PrimitiveLongList());
            recordMap.put("BASE_GPU_WORK_NS", new PrimitiveLongList());
        }

        this.osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        this.memBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.lastTimestampNs = System.nanoTime();
    }

    public void setPhase(String phaseName) {
        synchronized (lock) {
            PrimitiveLongList baseList = recordMap.get("CPU_USAGE_PERCENT");
            int currentTick = (baseList != null) ? baseList.size() : 0;
            phaseMap.put(currentTick, phaseName);
        }
    }

    public void update() {
        long now = System.nanoTime();
        long frameTimeNs = (lastTimestampNs > 0) ? (now - lastTimestampNs) : 0;
        lastTimestampNs = now;

        frameCounter++;
        if (frameCounter % mode.getInterval() != 0) {
            return;
        }

        // CPU 사용률 (0~100 스케일 변환)
        double cpuLoad = (osBean != null) ? osBean.getCpuLoad() : -1.0;
        long cpuPercent = (cpuLoad >= 0) ? (long) (cpuLoad * 100) : 0L;

        // 메모리 (Bytes)
        long heapUsed = memBean.getHeapMemoryUsage().getUsed();
        long nonHeapUsed = memBean.getNonHeapMemoryUsage().getUsed();

        long totalGcTime = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            long time = gc.getCollectionTime();
            if (time > 0) totalGcTime += time;
        }

        long baseCpu = (baseProvider != null) ? baseProvider.getCpuWorkTimeNs() : 0L;
        long baseGpu = (baseProvider != null) ? baseProvider.getGpuWorkTimeNs() : 0L;

        synchronized (lock) {
            recordMap.get("CPU_USAGE_PERCENT").add(cpuPercent);
            recordMap.get("HEAP_USED_BYTES").add(heapUsed);
            recordMap.get("NON_HEAP_USED_BYTES").add(nonHeapUsed);
            recordMap.get("GC_TIME_MS").add(totalGcTime);
            recordMap.get("FRAME_TIME_NS").add(frameTimeNs);
            recordMap.get("BASE_CPU_WORK_NS").add(baseCpu);
            recordMap.get("BASE_GPU_WORK_NS").add(baseGpu);
        }
    }

    public Map<String, Long[]> getRecordsAsArrays() {
        Map<String, Long[]> arrayMap = new LinkedHashMap<>();
        synchronized (lock) {
            for (Map.Entry<String, PrimitiveLongList> entry : recordMap.entrySet()) {
                arrayMap.put(entry.getKey(), entry.getValue().toObjectArray());
            }
        }
        return arrayMap;
    }

    public Map<Integer, String> getPhaseMap() {
        synchronized (lock) {
            return Collections.unmodifiableMap(new TreeMap<>(phaseMap));
        }
    }

    public void exit(String fileName) {
        String projectFolder = InternalUtils.getProjectFolder();
        File folder = new File(projectFolder);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        File dumpFile = new File(folder, fileName+".fwD");
        Properties prop = new Properties();

        synchronized (lock) {
            PrimitiveLongList baseList = recordMap.get("CPU_USAGE_PERCENT");
            int totalTicks = (baseList != null) ? baseList.size() : 0;

            prop.setProperty("meta.totalTicks", String.valueOf(totalTicks));
            prop.setProperty("meta.captureMode", mode.name());

            StringBuilder phaseBuilder = new StringBuilder();
            for (Map.Entry<Integer, String> entry : phaseMap.entrySet()) {
                if (phaseBuilder.length() > 0) phaseBuilder.append(";");
                phaseBuilder.append(entry.getKey()).append(":").append(entry.getValue());
            }
            prop.setProperty("phases", phaseBuilder.toString());

            for (Map.Entry<String, PrimitiveLongList> entry : recordMap.entrySet()) {
                StringBuilder sb = new StringBuilder();
                entry.getValue().writeCsv(sb);
                prop.setProperty("data." + entry.getKey(), sb.toString());
            }
        }

        try (OutputStream out = new FileOutputStream(dumpFile)) {
            prop.store(out, "Performance Profile Dump Data");
            System.out.println("[Profiler] 덤프 완료: " + dumpFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("[Profiler] 덤프 파일 저장 실패: " + dumpFile.getAbsolutePath());
            e.printStackTrace();
        }
    }

    public interface BaseWorkTimeProvider {
        long getCpuWorkTimeNs();
        long getGpuWorkTimeNs();
    }
}