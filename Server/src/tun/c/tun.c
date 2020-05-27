#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <sys/ioctl.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <errno.h>
#include <fcntl.h>

#ifdef __linux__

#include <net/if.h>
#include <linux/if_tun.h>

static void set_interface(char *name, int descriptor)
{
    struct ifreq ifr;
    memset(&ifr, 0, sizeof(ifr));
    ifr.ifr_flags = IFF_TUN | IFF_NO_PI;
    strncpy(ifr.ifr_name, name, sizeof(ifr.ifr_name));

    if (ioctl(descriptor, TUNSETIFF, &ifr)) {
        perror("Cannot get TUN interface");
        exit(1);
    }
}

#else

#error No implementation for Windows or Mac yet.

#endif

JNIEXPORT void JNICALL
Java_com_mancersoft_litevpnserver_InterfaceManager_ioctl(JNIEnv *env, jobject obj, jint descriptor)
{
    set_interface("tun0", descriptor);
}