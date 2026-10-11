/*
 * Hand-written iperf_config.h for Android (NDK cross-compile — no
 * ./configure run against the NDK toolchain). Socket-option availability
 * below is carried over from a prior Android-only project's config that was
 * empirically verified to build and run real iperf3 client tests on actual
 * Android hardware (not derived from a host-machine ./configure run, whose
 * BSD/Darwin socket options don't reflect Android's Linux kernel/NDK
 * surface at all). Updated for iperf3 3.21: version strings bumped, and the
 * handful of macros 3.21 added since (CAN_BIND_TO_DEVICE,
 * HAVE_SOCKET_SHUTDOWN_SHUT_WR, HAVE_UDP_GRO, HAVE_UDP_SEGMENT) are left
 * undefined — safe, conservative defaults; an undefined macro just disables
 * that one optional feature, it doesn't break the build.
 *
 * OpenSSL/SCTP are intentionally unavailable — see
 * IPERF3_NATIVE_INTEGRATION.md §6.6 (this project doesn't use iperf3's auth
 * or SCTP support, and dropping both removes a whole class of per-platform
 * dependency-sourcing problems).
 */

#define CAN_BIND_TO_DEVICE 0

#define HAVE_CLOCK_GETTIME 1
/* #undef HAVE_CLOCK_NANOSLEEP */
/* #undef HAVE_CPUSET_SETAFFINITY */
#define HAVE_CPU_AFFINITY 1

/* #undef HAVE_DAEMON */
/* #undef HAVE_DONT_FRAGMENT */
#define HAVE_DLFCN_H 1
#define HAVE_ENDIAN_H 1
/* #undef HAVE_FLOWLABEL */
#define HAVE_GETLINE 1
#define HAVE_INTTYPES_H 1
/* #undef HAVE_IPPROTO_MPTCP */
/* #undef HAVE_IP_BOUND_IF */
/* #undef HAVE_IP_DONTFRAG */
/* #undef HAVE_IP_DONTFRAGMENT */
/* #undef HAVE_IP_MTU_DISCOVER */
/* #undef HAVE_LINUX_TCP_H */
/* #undef HAVE_MSG_TRUNC */
#define HAVE_NANOSLEEP 1

/* #undef HAVE_NETINET_SCTP_H */
#define HAVE_POLL_H 1
#define HAVE_PTHREAD 1
/* #undef HAVE_PTHREAD_PRIO_INHERIT */
/* #undef HAVE_SCHED_SETAFFINITY */
/* #undef HAVE_SCTP_H */
/* #undef HAVE_SENDFILE */
/* #undef HAVE_SETPROCESSAFFINITYMASK */
/* #undef HAVE_SOCKET_SHUTDOWN_SHUT_WR */
/* #undef HAVE_SO_BINDTODEVICE */
#define HAVE_SO_MAX_PACING_RATE 1
/* #undef HAVE_SSL */
#define HAVE_STDATOMIC_H 1
#define HAVE_STDINT_H 1
#define HAVE_STDIO_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRINGS_H 1
#define HAVE_STRING_H 1

/* #undef HAVE_STRUCT_SCTP_ASSOC_VALUE */

/* #undef HAVE_SYS_ENDIAN_H */
#define HAVE_SYS_SOCKET_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1

#define HAVE_TCP_CONGESTION 1
/* #undef HAVE_TCP_INFO_SND_WND */
/* #undef HAVE_TCP_KEEPALIVE */
/* #undef HAVE_TCP_USER_TIMEOUT */
/* #undef HAVE_UDP_GRO */
/* #undef HAVE_UDP_SEGMENT */

#define HAVE_UNISTD_H 1

#define LT_OBJDIR ".libs/"

#define PACKAGE "iperf"
#define PACKAGE_BUGREPORT "https://github.com/esnet/iperf"
#define PACKAGE_NAME "iperf"
#define PACKAGE_STRING "iperf 3.21"
#define PACKAGE_TARNAME "iperf"
#define PACKAGE_URL "https://software.es.net/iperf/"
#define PACKAGE_VERSION "3.21"
#define VERSION "3.21"

#define STDC_HEADERS 1

/* #undef const */
