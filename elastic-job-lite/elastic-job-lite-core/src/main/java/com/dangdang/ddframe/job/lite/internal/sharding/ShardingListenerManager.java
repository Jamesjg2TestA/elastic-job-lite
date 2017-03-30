/*
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.job.lite.internal.sharding;

import com.dangdang.ddframe.job.lite.internal.config.ConfigurationNode;
import com.dangdang.ddframe.job.lite.internal.config.LiteJobConfigurationGsonFactory;
import com.dangdang.ddframe.job.lite.internal.execution.ExecutionNode;
import com.dangdang.ddframe.job.lite.internal.execution.ExecutionService;
import com.dangdang.ddframe.job.lite.internal.listener.AbstractJobListener;
import com.dangdang.ddframe.job.lite.internal.listener.AbstractListenerManager;
import com.dangdang.ddframe.job.lite.internal.instance.InstanceNode;
import com.dangdang.ddframe.job.lite.internal.server.ServerNode;
import com.dangdang.ddframe.job.reg.base.CoordinatorRegistryCenter;
import lombok.Setter;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.TreeCacheEvent;

/**
 * 分片监听管理器.
 * 
 * @author zhangliang
 */
public class ShardingListenerManager extends AbstractListenerManager {
    
    private final ShardingService shardingService;
    
    private final ExecutionService executionService;
    
    private final ConfigurationNode configNode;
    
    private final InstanceNode instanceNode;
    
    private final ServerNode serverNode;
    
    private final ExecutionNode executionNode;
    
    @Setter
    private int currentShardingTotalCount;
    
    public ShardingListenerManager(final CoordinatorRegistryCenter regCenter, final String jobName) {
        super(regCenter, jobName);
        shardingService = new ShardingService(regCenter, jobName);
        executionService = new ExecutionService(regCenter, jobName);
        configNode = new ConfigurationNode(jobName);
        instanceNode = new InstanceNode(jobName);
        serverNode = new ServerNode(jobName);
        executionNode = new ExecutionNode(jobName);
    }
    
    @Override
    public void start() {
        addDataListener(new ShardingTotalCountChangedJobListener());
        addDataListener(new ListenServersChangedJobListener());
    }
    
    class ShardingTotalCountChangedJobListener extends AbstractJobListener {
        
        @Override
        protected void dataChanged(final CuratorFramework client, final TreeCacheEvent event, final String path) {
            if (configNode.isConfigPath(path) && 0 != currentShardingTotalCount) {
                int newShardingTotalCount = LiteJobConfigurationGsonFactory.fromJson(new String(event.getData().getData())).getTypeConfig().getCoreConfig().getShardingTotalCount();
                if (newShardingTotalCount != currentShardingTotalCount) {
                    shardingService.setReshardingFlag();
                    executionService.setNeedFixExecutionInfoFlag();
                    currentShardingTotalCount = newShardingTotalCount;
                }
            }
        }
    }
    
    class ListenServersChangedJobListener extends AbstractJobListener {
        
        @Override
        protected void dataChanged(final CuratorFramework client, final TreeCacheEvent event, final String path) {
            if (isInstanceChange(event, path) || isServerChange(path)) {
                shardingService.setReshardingFlag();
            }
        }
        
        private boolean isInstanceChange(final TreeCacheEvent event, final String path) {
            return instanceNode.isInstancePath(path) && TreeCacheEvent.Type.NODE_UPDATED != event.getType();
        }
        
        private boolean isServerChange(final String path) {
            return serverNode.isServerPath(path);
        }
    }
}
