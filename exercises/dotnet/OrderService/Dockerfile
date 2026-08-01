# ---------------------------------------------------------------------------
# OrderService — multi-stage build
# Build context: exercises/dotnet/ (parent dir, so SharedObservability is accessible)
# ---------------------------------------------------------------------------

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Copy project files first for layer caching on restore
COPY ["SharedObservability/SharedObservability.csproj", "SharedObservability/"]
COPY ["OrderService/OrderService.csproj", "OrderService/"]
RUN dotnet restore "OrderService/OrderService.csproj"

# Copy source and publish
COPY SharedObservability/ SharedObservability/
COPY OrderService/ OrderService/
RUN dotnet publish "OrderService/OrderService.csproj" -c Release -o /app/publish

# Runtime — prefer UBI 10 .NET image when available
# FROM registry.access.redhat.com/ubi10/dotnet-100:latest
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /opt/app-root/src
COPY --from=build /app/publish .
EXPOSE 8080
ENTRYPOINT ["dotnet", "OrderService.dll"]
