/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */

package freenet.support;

import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

/**
 * A class to control the global priority of the current process.
 * Microsoft suggests flagging daemon/server processes with the BACKGROUND_MODE
 * priority class so that they don't interfere with the responsiveness of the
 * rest of the system. This is especially important when freenet is started at
 * system startup.
 * We use JNA to call the OS libraries directly without needing JNI wrappers.
 * Its usage is really simple: just call ProcessPriority.enterBackgroundMode().
 * If the OS doesn't support it or if the process doesn't have the appropriate
 * permissions, the above call is simply a no-op.
 *
 */
 
public class ProcessPriority {
    private static volatile boolean background = false;
    
    /// Windows interface (kernel32.dll) ///
    private static class WindowsHolder {
        static { Native.register("kernel32"); }
        /* HANDLE -> Pointer, DWORD -> int */
        private static native boolean SetPriorityClass(Pointer hProcess, int dwPriorityClass);
        private static native Pointer GetCurrentProcess();
        private static native int GetLastError();

        final static int PROCESS_MODE_BACKGROUND_BEGIN         = 0x00100000;
        final static int PROCESS_MODE_BACKGROUND_END           = 0x00200000;
        final static int ERROR_PROCESS_MODE_ALREADY_BACKGROUND = 402;
        final static int ERROR_PROCESS_MODE_NOT_BACKGROUND     = 403;
    }

    private static class LinuxHolder {
        static { Native.register(Platform.C_LIBRARY_NAME); }

        private static native int setpriority(int which, int who, int prio);
        final static int PRIO_PROCESS = 0;
        final static int MYSELF = 0;
        final static int LOWER_PRIORITY = 10;
    }

    private static class OSXHolder {
        static { Native.register(Platform.C_LIBRARY_NAME); }

        private static native int setpriority(int which, int who, int prio);
        final static int PRIO_DARWIN_THREAD = 3;
        final static int MYSELF = 0;
        final static int PRIO_DARWIN_NORMAL = 0;
        final static int PRIO_DARWIN_BG = 0x1000;
    }


    public static boolean enterBackgroundMode() {
        if (!background) {
            if (Platform.isWindows()) {
                if (WindowsHolder.SetPriorityClass(WindowsHolder.GetCurrentProcess(), WindowsHolder.PROCESS_MODE_BACKGROUND_BEGIN)) {
                    System.out.println("SetPriorityClass() succeeded!");
                    return background = true;
                } else if (WindowsHolder.GetLastError() == WindowsHolder.ERROR_PROCESS_MODE_ALREADY_BACKGROUND) {
                    System.err.println("SetPriorityClass() failed :"+WindowsHolder.GetLastError());
                    return false;
                }
            } else if (Platform.isLinux()) {
                return handleReturn(LinuxHolder.setpriority(LinuxHolder.PRIO_PROCESS, LinuxHolder.MYSELF, LinuxHolder.LOWER_PRIORITY));

            } else if (Platform.isMac()) {
                return handleReturn(OSXHolder.setpriority(OSXHolder.PRIO_DARWIN_THREAD, OSXHolder.MYSELF, OSXHolder.PRIO_DARWIN_BG));
            }
        }
        return background;
    }

    private static boolean handleReturn(int ret) {
        if (ret == 0) {
            System.out.println("setpriority() succeeded!");
            return background = true;
        } else {
            System.err.println("setpriority() failed :"+ret);
            return false;
        }
    }

}

