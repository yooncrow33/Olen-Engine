package com.fw.main.utils.platform.system.performance;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.CompilationMXBean;
import java.lang.management.ThreadMXBean;
import java.awt.GraphicsEnvironment;
import java.awt.GraphicsDevice;
import java.awt.DisplayMode;
import java.util.*;

import com.fw.internal.sys.base.view.AccessConsole;
import com.fw.internal.utils.InternalUtils;
import com.fw.main.utils.platform.system.console.Console;
import com.sun.management.OperatingSystemMXBean;

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
            accessConsole.getConsole().addLog(Console.LogType.ERROR, "DO_NOT option bypassed for PerformanceRecorder. Overriding with EVERY_SECOND recording.");
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

        double cpuLoad = (osBean != null) ? osBean.getCpuLoad() : -1.0;
        long cpuPercent = (cpuLoad >= 0) ? (long) (cpuLoad * 100) : 0L;

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

        File dumpFile = new File(folder, fileName + ".fwD");
        Properties prop = new Properties();

        try {
            // 1. OS & 계정 & 로케일 환경
            prop.setProperty("sysinfo.OS_NAME", System.getProperty("os.name", "Unknown"));
            prop.setProperty("sysinfo.OS_VERSION", System.getProperty("os.version", "Unknown"));
            prop.setProperty("sysinfo.OS_ARCH", System.getProperty("os.arch", "Unknown"));
            prop.setProperty("sysinfo.OS_PATCH", System.getProperty("sun.os.patch.level", "None"));
            prop.setProperty("sysinfo.USER_NAME", System.getProperty("user.name", "Unknown"));
            prop.setProperty("sysinfo.USER_HOME", System.getProperty("user.home", "Unknown"));
            prop.setProperty("sysinfo.USER_DIR", System.getProperty("user.dir", "Unknown"));
            prop.setProperty("sysinfo.TMP_DIR", System.getProperty("java.io.tmpdir", "Unknown"));
            prop.setProperty("sysinfo.FILE_ENCODING", System.getProperty("file.encoding", "Unknown"));
            prop.setProperty("sysinfo.TIMEZONE", TimeZone.getDefault().getID());

            String compName = System.getenv("COMPUTERNAME");
            if (compName == null) compName = System.getenv("HOSTNAME");
            prop.setProperty("sysinfo.COMPUTER_NAME", (compName != null) ? compName : "Unknown");
            prop.setProperty("sysinfo.USER_DOMAIN", Optional.ofNullable(System.getenv("USERDOMAIN")).orElse("Local"));
            prop.setProperty("sysinfo.SESSION_NAME", Optional.ofNullable(System.getenv("SESSIONNAME")).orElse("Console"));

            // 2. CPU & 프로세스 식별
            prop.setProperty("sysinfo.PROCESS_PID", String.valueOf(ProcessHandle.current().pid()));
            prop.setProperty("sysinfo.CPU_CORES", String.valueOf(Runtime.getRuntime().availableProcessors()));
            String procId = System.getenv("PROCESSOR_IDENTIFIER");
            if (procId == null) procId = System.getenv("PROCESSOR_ARCHITECTURE");
            prop.setProperty("sysinfo.PROCESSOR_ID", (procId != null) ? procId : "Unknown");
            prop.setProperty("sysinfo.PROCESSOR_LEVEL", Optional.ofNullable(System.getenv("PROCESSOR_LEVEL")).orElse("N/A"));
            prop.setProperty("sysinfo.PROCESSOR_REV", Optional.ofNullable(System.getenv("PROCESSOR_REVISION")).orElse("N/A"));

            // 3. 네트워크 인터페이스
            try {
                java.net.InetAddress local = java.net.InetAddress.getLocalHost();
                prop.setProperty("sysinfo.HOST_NAME", local.getHostName());
                prop.setProperty("sysinfo.HOST_IP", local.getHostAddress());

                Enumeration<java.net.NetworkInterface> nifs = java.net.NetworkInterface.getNetworkInterfaces();
                int netCount = 0;
                while (nifs != null && nifs.hasMoreElements()) {
                    java.net.NetworkInterface nif = nifs.nextElement();
                    if (!nif.isLoopback() && nif.isUp()) netCount++;
                }
                prop.setProperty("sysinfo.ACTIVE_NET_IF_COUNT", String.valueOf(netCount));
            } catch (Throwable ignored) {}

            // 4. 그래픽 & 디스플레이
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] gds = ge.getScreenDevices();
            prop.setProperty("sysinfo.SCREEN_COUNT", String.valueOf(gds.length));
            if (gds.length > 0) {
                GraphicsDevice gd = ge.getDefaultScreenDevice();
                DisplayMode dm = gd.getDisplayMode();
                long vram = gd.getAvailableAcceleratedMemory();
                prop.setProperty("sysinfo.DISPLAY_INFO", dm.getWidth() + "x" + dm.getHeight() + " @" + dm.getRefreshRate() + "Hz (" + dm.getBitDepth() + "bit)");
                if (vram >= 0) prop.setProperty("sysinfo.ACCEL_VRAM", formatBytes(vram));
            }

            // 5. JVM 런타임 & 스레드
            prop.setProperty("sysinfo.JAVA_VERSION", System.getProperty("java.version", "Unknown"));
            prop.setProperty("sysinfo.JAVA_VENDOR", System.getProperty("java.vendor", "Unknown"));
            prop.setProperty("sysinfo.JAVA_VM_NAME", System.getProperty("java.vm.name", "Unknown"));
            prop.setProperty("sysinfo.JAVA_HOME", System.getProperty("java.home", "Unknown"));

            RuntimeMXBean rBean = ManagementFactory.getRuntimeMXBean();
            if (rBean != null) {
                prop.setProperty("sysinfo.JVM_UPTIME", (rBean.getUptime() / 1000) + "s");
                prop.setProperty("sysinfo.JVM_INPUT_ARGS", String.join(" ", rBean.getInputArguments()));
            }

            CompilationMXBean cBean = ManagementFactory.getCompilationMXBean();
            if (cBean != null) {
                prop.setProperty("sysinfo.JIT_COMPILER", cBean.getName());
            }

            ThreadMXBean tBean = ManagementFactory.getThreadMXBean();
            if (tBean != null) {
                prop.setProperty("sysinfo.THREAD_COUNT", String.valueOf(tBean.getThreadCount()));
                prop.setProperty("sysinfo.PEAK_THREAD_COUNT", String.valueOf(tBean.getPeakThreadCount()));
                prop.setProperty("sysinfo.DAEMON_THREAD_COUNT", String.valueOf(tBean.getDaemonThreadCount()));
            }

            // 6. 메모리 사양 (물리/가상/스왑/JVM)
            prop.setProperty("sysinfo.JVM_MAX_MEMORY", String.valueOf(Runtime.getRuntime().maxMemory()));
            prop.setProperty("sysinfo.JVM_TOTAL_MEMORY", String.valueOf(Runtime.getRuntime().totalMemory()));
            prop.setProperty("sysinfo.JVM_FREE_MEMORY", String.valueOf(Runtime.getRuntime().freeMemory()));

            if (osBean != null) {
                long totalPhysical = osBean.getTotalPhysicalMemorySize();
                long freePhysical = osBean.getFreePhysicalMemorySize();
                long committedVm = osBean.getCommittedVirtualMemorySize();
                long totalSwap = osBean.getTotalSwapSpaceSize();
                long freeSwap = osBean.getFreeSwapSpaceSize();

                if (totalPhysical > 0) prop.setProperty("sysinfo.TOTAL_PHYSICAL_MEM", String.valueOf(totalPhysical));
                if (freePhysical > 0) prop.setProperty("sysinfo.FREE_PHYSICAL_MEM", String.valueOf(freePhysical));
                if (committedVm > 0) prop.setProperty("sysinfo.COMMITTED_VM", String.valueOf(committedVm));
                if (totalSwap > 0) prop.setProperty("sysinfo.TOTAL_SWAP_MEM", String.valueOf(totalSwap));
                if (freeSwap > 0) prop.setProperty("sysinfo.FREE_SWAP_MEM", String.valueOf(freeSwap));
            }

            // 7. 가비지 컬렉터 목록
            StringBuilder gcNames = new StringBuilder();
            for (GarbageCollectorMXBean gc : gcBeans) {
                if (gcNames.length() > 0) gcNames.append(", ");
                gcNames.append(gc.getName());
            }
            prop.setProperty("sysinfo.GC_NAMES", gcNames.toString());

            // 8. 디스크 볼륨 목록 전수 순회
            File[] roots = File.listRoots();
            if (roots != null) {
                StringBuilder diskSummary = new StringBuilder();
                for (File root : roots) {
                    if (diskSummary.length() > 0) diskSummary.append(" | ");
                    diskSummary.append(root.getAbsolutePath())
                            .append(" Free: ").append(formatBytes(root.getFreeSpace()))
                            .append("/").append(formatBytes(root.getTotalSpace()));
                }
                prop.setProperty("sysinfo.DISK_VOLUMES", diskSummary.toString());
            }
        } catch (Throwable ignored) {}

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

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) return String.format("%.1fGB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024) return String.format("%.1fMB", bytes / (1024.0 * 1024));
        return bytes + "B";
    }

    public interface BaseWorkTimeProvider {
        long getCpuWorkTimeNs();
        long getGpuWorkTimeNs();
    }
}