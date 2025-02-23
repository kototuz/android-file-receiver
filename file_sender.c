#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/sendfile.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <dirent.h>
#include <stdbool.h>
#include <libgen.h>

#define PORT 6969

#define ARRAY_LEN(xs) (sizeof(xs)/sizeof(xs[0]))

bool write_bytes(const uint8_t *data, size_t len, int target_fd)
{
    off_t remain = len;
    off_t offset = 0;
    for (;;) {
        ssize_t res_len = write(target_fd, &data[offset], remain);
        if (res_len == -1) {
            perror("Failed writing bytes");
            return false;
        }

        remain -= res_len;
        if (remain == 0) break;
        offset += res_len;
    }

    return true;
}

bool write_file(char *file_name, off_t file_size, int target_fd)
{
    int fd = open(file_name, O_RDONLY);
    if (fd == -1) {
        perror("Opening file failed");
        return false;
    }

    char *base_file_name = basename(file_name);
    union { size_t self; uint8_t bytes[sizeof(size_t)]; }
    size_t_as_bytes = { strlen(base_file_name) };
    if (!write_bytes(size_t_as_bytes.bytes, sizeof(size_t), target_fd))
        return false;
    if (!write_bytes((uint8_t *)base_file_name, size_t_as_bytes.self, target_fd))
        return false;

    union { off_t self; uint8_t bytes[sizeof(off_t)]; }
    off_t_as_bytes = { file_size };
    if (!write_bytes(off_t_as_bytes.bytes, sizeof(off_t), target_fd))
        return false;

    off_t remain = file_size;
    off_t offset = 0;
    for (;;) {
        ssize_t res_len = sendfile(target_fd, fd, &offset, remain);
        if (res_len == -1) {
            perror("Failed sending file");
            return false;
        }

        remain -= res_len;
        if (remain == 0) break;
        offset += res_len;
    }

    close(fd);
    return true;
}

int main(int argc, char *argv[])
{
    if (argc == 1) {
        fprintf(stderr, "usage: %s <file|dir>...\n", argv[0]);
        return 1;
    }

    int sockfd = socket(AF_INET, SOCK_STREAM, 0);
    if (sockfd == -1) {
        perror("Opening socket failed");
        return 1;
    }

    struct sockaddr_in server_addr;
    server_addr.sin_family = AF_INET;
    server_addr.sin_port = htons(PORT);
    inet_pton(AF_INET, SERVER_IP_ADDR, &server_addr.sin_addr);

    if (connect(sockfd, (struct sockaddr *)&server_addr, sizeof(server_addr)) == -1) {
        perror("Connection failed");
        close(sockfd);
        return 1;
    }

    int optval = 1;
    if (setsockopt(sockfd, SOL_TCP, TCP_CORK, &optval, sizeof(optval)) == -1) {
        perror("Failed to set 'TCP_CORK' option to socket");
    }

    puts("Connected to "SERVER_IP_ADDR);

    for (int i = 1; i < argc; i++) {
        struct stat st;
        if (stat(argv[i], &st) == -1) {
            fprintf(stderr, "Stat file '%s' failed: %s\n", argv[i], strerror(errno));
            continue;
        }

        if (S_ISDIR(st.st_mode)) {
            DIR *dir = opendir(argv[i]);
            if (dir == NULL) {
                fprintf(stderr, "Failed opening directory '%s': %s\n", argv[i], strerror(errno));
                continue;
            }

            struct dirent *ent;
            while ((ent = readdir(dir)) != NULL) {
                if (strcmp(ent->d_name, ".") == 0 || strcmp(ent->d_name, "..") == 0) {
                    continue;
                }

                if (ent->d_type == DT_DIR) continue;

                char file_path[PATH_MAX];
                strcpy(file_path, argv[i]);
                strcat(file_path, "/");
                strcat(file_path, ent->d_name);

                if (stat(file_path, &st) == -1) {
                    fprintf(stderr, "Failed stat file '%s': %s\n", ent->d_name, strerror(errno));
                    continue;
                }

                printf("Sending '%s'(%zub)...\n", file_path, st.st_size);
                write_file(file_path, st.st_size, sockfd);
            }

            closedir(dir);
        } else {
            printf("Sending '%s'(%zub)...\n", argv[i], st.st_size);
            write_file(argv[i], st.st_size, sockfd);
        }
    }

    optval = 0;
    if (setsockopt(sockfd, SOL_TCP, TCP_CORK, &optval, sizeof(optval)) == -1) {
        perror("Failed to disable 'TCP_CORK' option in socket");
    }

    shutdown(sockfd, SHUT_WR);
    close(sockfd);
    return 0;
}
