# ---------------------------------------------------------------------------
# InventoryService — multi-stage build
# Build context: exercises/dotnet/ (parent dir, so SharedObservability is accessible)
# ---------------------------------------------------------------------------

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY ["SharedObservability/SharedObservability.csproj", "SharedObservability/"]
COPY ["InventoryService/InventoryService.csproj", "InventoryService/"]
RUN dotnet restore "InventoryService/InventoryService.csproj"

COPY SharedObservability/ SharedObservability/
COPY InventoryService/ InventoryService/
RUN dotnet publish "InventoryService/InventoryService.csproj" -c Release -o /app/publish

# Runtime — prefer UBI 10 .NET image when available
# FROM registry.access.redhat.com/ubi10/dotnet-100:latest
FROM mcr.microsoft.com/dotnet/aspnet:10.0
WORKDIR /opt/app-root/src
COPY --from=build /app/publish .
EXPOSE 8081
ENTRYPOINT ["dotnet", "InventoryService.dll"]
