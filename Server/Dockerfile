FROM lite-vpn-build:latest AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build --no-daemon
FROM lite-vpn-server:latest
COPY --from=build /home/gradle/src/build/libs/*.jar /app/lite-vpn-server.jar
COPY --from=build /home/gradle/src/build/libs/tun /app/tun
WORKDIR /app
ENTRYPOINT ["java","-jar","/app/lite-vpn-server.jar"]