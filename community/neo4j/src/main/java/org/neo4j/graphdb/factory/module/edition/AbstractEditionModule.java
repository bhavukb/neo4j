/*
 * Copyright (c) "Neo4j"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphdb.factory.module.edition;

import static org.neo4j.configuration.GraphDatabaseSettings.SYSTEM_DATABASE_NAME;
import static org.neo4j.kernel.database.NamedDatabaseId.NAMED_SYSTEM_DATABASE_ID;
import static org.neo4j.procedure.impl.temporal.TemporalFunction.registerTemporalFunctions;

import java.util.function.Supplier;
import org.neo4j.bolt.dbapi.BoltGraphDatabaseManagementServiceSPI;
import org.neo4j.collection.Dependencies;
import org.neo4j.common.DependencyResolver;
import org.neo4j.configuration.Config;
import org.neo4j.configuration.connectors.ConnectorPortRegister;
import org.neo4j.dbms.api.DatabaseManagementException;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.database.DatabaseContext;
import org.neo4j.dbms.database.DatabaseContextProvider;
import org.neo4j.dbms.database.DatabaseInfoService;
import org.neo4j.dbms.database.DbmsRuntimeRepository;
import org.neo4j.dbms.database.DbmsRuntimeSystemGraphComponent;
import org.neo4j.dbms.database.DefaultSystemGraphInitializer;
import org.neo4j.dbms.database.StandaloneDbmsRuntimeRepository;
import org.neo4j.dbms.database.SystemGraphComponents;
import org.neo4j.dbms.database.SystemGraphInitializer;
import org.neo4j.exceptions.KernelException;
import org.neo4j.graphdb.DatabaseShutdownException;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.facade.DatabaseManagementServiceFactory;
import org.neo4j.graphdb.factory.module.GlobalModule;
import org.neo4j.graphdb.factory.module.id.IdContextFactory;
import org.neo4j.graphdb.factory.module.id.IdContextFactoryBuilder;
import org.neo4j.internal.collector.DataCollectorProcedures;
import org.neo4j.kernel.api.net.DefaultNetworkConnectionTracker;
import org.neo4j.kernel.api.net.NetworkConnectionTracker;
import org.neo4j.kernel.api.procedure.GlobalProcedures;
import org.neo4j.kernel.api.security.AuthManager;
import org.neo4j.kernel.api.security.provider.SecurityProvider;
import org.neo4j.kernel.database.DatabaseReferenceRepository;
import org.neo4j.kernel.database.DefaultDatabaseResolver;
import org.neo4j.kernel.impl.factory.DbmsInfo;
import org.neo4j.kernel.impl.transaction.stats.DatabaseTransactionStats;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.logging.InternalLogProvider;
import org.neo4j.logging.internal.LogService;
import org.neo4j.monitoring.Monitors;
import org.neo4j.procedure.builtin.BuiltInDbmsProcedures;
import org.neo4j.procedure.builtin.BuiltInProcedures;
import org.neo4j.procedure.builtin.FulltextProcedures;
import org.neo4j.procedure.builtin.TokenProcedures;
import org.neo4j.procedure.builtin.routing.AbstractRoutingProcedureInstaller;
import org.neo4j.procedure.builtin.routing.ClientRoutingDomainChecker;
import org.neo4j.procedure.builtin.routing.RoutingOption;
import org.neo4j.procedure.builtin.routing.RoutingTableTTLProvider;
import org.neo4j.procedure.builtin.routing.ServerSideRoutingTableProvider;
import org.neo4j.procedure.builtin.routing.SimpleClientRoutingDomainChecker;
import org.neo4j.procedure.builtin.routing.SingleAddressRoutingTableProvider;
import org.neo4j.procedure.impl.ProcedureConfig;
import org.neo4j.server.config.AuthConfigProvider;
import org.neo4j.time.SystemNanoClock;

/**
 * Edition module for {@link DatabaseManagementServiceFactory}. Implementations of this class
 * need to create all the services that would be specific for a particular edition of the database.
 */
public abstract class AbstractEditionModule {
    protected NetworkConnectionTracker connectionTracker;
    protected SecurityProvider securityProvider;
    protected DefaultDatabaseResolver defaultDatabaseResolver;
    protected DatabaseReferenceRepository databaseReferenceRepo;

    public void registerProcedures(
            GlobalProcedures globalProcedures,
            ProcedureConfig procedureConfig,
            GlobalModule globalModule,
            DatabaseContextProvider<?> databaseContextProvider)
            throws KernelException {
        globalProcedures.registerProcedure(BuiltInProcedures.class);
        globalProcedures.registerProcedure(TokenProcedures.class);
        globalProcedures.registerProcedure(BuiltInDbmsProcedures.class);
        globalProcedures.registerProcedure(FulltextProcedures.class);
        globalProcedures.registerProcedure(DataCollectorProcedures.class);
        registerTemporalFunctions(globalProcedures, procedureConfig);

        registerEditionSpecificProcedures(globalProcedures, databaseContextProvider);
        AbstractRoutingProcedureInstaller routingProcedureInstaller = createRoutingProcedureInstaller(
                globalModule,
                databaseContextProvider,
                globalModule.getGlobalDependencies().resolveDependency(ClientRoutingDomainChecker.class));
        routingProcedureInstaller.install(globalProcedures);
    }

    public ClientRoutingDomainChecker createClientRoutingDomainChecker(GlobalModule globalModule) {
        Config config = globalModule.getGlobalConfig();
        var domainChecker = SimpleClientRoutingDomainChecker.fromConfig(
                config, globalModule.getLogService().getInternalLogProvider());
        globalModule.getGlobalDependencies().satisfyDependencies(domainChecker);
        return domainChecker;
    }

    protected void registerEditionSpecificProcedures(
            GlobalProcedures globalProcedures, DatabaseContextProvider<?> databaseContextProvider)
            throws KernelException {}

    protected abstract AbstractRoutingProcedureInstaller createRoutingProcedureInstaller(
            GlobalModule globalModule,
            DatabaseContextProvider<?> databaseContextProvider,
            ClientRoutingDomainChecker clientRoutingDomainChecker);

    protected abstract AuthConfigProvider createAuthConfigProvider(GlobalModule globalModule);

    public abstract <DB extends DatabaseContext> DatabaseContextProvider<DB> createDatabaseContextProvider(
            GlobalModule globalModule);

    public abstract void registerSystemGraphInitializer(GlobalModule globalModule);

    public abstract void registerSystemGraphComponents(
            SystemGraphComponents systemGraphComponents, GlobalModule globalModule);

    public abstract void createSecurityModule(GlobalModule globalModule);

    protected static NetworkConnectionTracker createConnectionTracker() {
        return new DefaultNetworkConnectionTracker();
    }

    public DatabaseTransactionStats.Factory getTransactionMonitorFactory() {
        return DatabaseTransactionStats::new;
    }

    public NetworkConnectionTracker getConnectionTracker() {
        return connectionTracker;
    }

    public SecurityProvider getSecurityProvider() {
        return securityProvider;
    }

    public void setSecurityProvider(SecurityProvider securityProvider) {
        this.securityProvider = securityProvider;
    }

    public abstract void createDefaultDatabaseResolver(GlobalModule globalModule);

    public void setDefaultDatabaseResolver(DefaultDatabaseResolver defaultDatabaseResolver) {
        this.defaultDatabaseResolver = defaultDatabaseResolver;
    }

    public DefaultDatabaseResolver getDefaultDatabaseResolver() {
        return defaultDatabaseResolver;
    }

    public abstract void bootstrapFabricServices();

    public abstract BoltGraphDatabaseManagementServiceSPI createBoltDatabaseManagementServiceProvider(
            Dependencies dependencies,
            DatabaseManagementService managementService,
            Monitors monitors,
            SystemNanoClock clock,
            LogService logService);

    public AuthManager getBoltAuthManager(DependencyResolver dependencyResolver) {
        return dependencyResolver.resolveDependency(AuthManager.class);
    }

    public AuthManager getBoltInClusterAuthManager() {
        return securityProvider.inClusterAuthManager();
    }

    public AuthManager getBoltLoopbackAuthManager() {
        return securityProvider.loopbackAuthManager();
    }

    public abstract Lifecycle createWebServer(
            DatabaseManagementService managementService,
            Dependencies globalDependencies,
            Config config,
            InternalLogProvider userLogProvider,
            DbmsInfo dbmsInfo);

    public DbmsRuntimeRepository createAndRegisterDbmsRuntimeRepository(
            GlobalModule globalModule,
            DatabaseContextProvider<?> databaseContextProvider,
            Dependencies dependencies,
            DbmsRuntimeSystemGraphComponent dbmsRuntimeSystemGraphComponent) {
        var dbmsRuntimeRepository =
                new StandaloneDbmsRuntimeRepository(databaseContextProvider, dbmsRuntimeSystemGraphComponent);
        globalModule
                .getTransactionEventListeners()
                .registerTransactionEventListener(SYSTEM_DATABASE_NAME, dbmsRuntimeRepository);
        return dbmsRuntimeRepository;
    }

    protected ServerSideRoutingTableProvider serverSideRoutingTableProvider(GlobalModule globalModule) {
        ConnectorPortRegister portRegister = globalModule.getConnectorPortRegister();
        Config config = globalModule.getGlobalConfig();
        InternalLogProvider logProvider = globalModule.getLogService().getInternalLogProvider();
        RoutingTableTTLProvider ttlProvider = RoutingTableTTLProvider.ttlFromConfig(config);
        return new SingleAddressRoutingTableProvider(
                portRegister, RoutingOption.ROUTE_WRITE_AND_READ, config, logProvider, ttlProvider);
    }

    public abstract DatabaseInfoService createDatabaseInfoService(DatabaseContextProvider<?> databaseContextProvider);

    public static <T> T tryResolveOrCreate(
            Class<T> clazz, DependencyResolver dependencies, Supplier<T> newInstanceMethod) {
        return dependencies.containsDependency(clazz) ? dependencies.resolveDependency(clazz) : newInstanceMethod.get();
    }

    public static IdContextFactory createIdContextFactory(GlobalModule globalModule) {
        return tryResolveOrCreate(
                IdContextFactory.class, globalModule.getExternalDependencyResolver(), () -> IdContextFactoryBuilder.of(
                                globalModule.getFileSystem(),
                                globalModule.getJobScheduler(),
                                globalModule.getGlobalConfig(),
                                globalModule.getTracers().getPageCacheTracer())
                        .withLogService(globalModule.getLogService())
                        .build());
    }

    protected static void registerSystemGraphInitializer(
            GlobalModule globalModule, DependencyResolver globalDependencies) {
        Supplier<GraphDatabaseService> systemSupplier = CommunityEditionModule.systemSupplier(globalDependencies);
        var systemGraphComponents = globalModule.getSystemGraphComponents();
        SystemGraphInitializer initializer = CommunityEditionModule.tryResolveOrCreate(
                SystemGraphInitializer.class,
                globalModule.getExternalDependencyResolver(),
                () -> new DefaultSystemGraphInitializer(systemSupplier, systemGraphComponents));
        globalModule.getGlobalDependencies().satisfyDependency(initializer);
        globalModule.getGlobalLife().add(initializer);
    }

    protected static Supplier<GraphDatabaseService> systemSupplier(DependencyResolver dependencies) {
        return () -> {
            DatabaseContextProvider<?> databaseContextProvider =
                    dependencies.resolveDependency(DatabaseContextProvider.class);
            return databaseContextProvider
                    .getDatabaseContext(NAMED_SYSTEM_DATABASE_ID)
                    .orElseThrow(() -> new DatabaseShutdownException(
                            new DatabaseManagementException("System database is not (yet) available")))
                    .databaseFacade();
        };
    }
}
