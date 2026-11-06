package com.drone.rental.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.drone.rental.common.Constants;
import com.drone.rental.command.*;
import com.drone.rental.common.ResultCode;
import com.drone.rental.common.exception.BusinessException;
import com.drone.rental.dto.DroneDTO;
import com.drone.rental.entity.Drone;
import com.drone.rental.entity.DroneStockLog;
import com.drone.rental.mapper.DroneMapper;
import com.drone.rental.mapper.DroneStockLogMapper;
import com.drone.rental.security.UserContext;
import com.drone.rental.service.DroneService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 无人机服务实现类
 */
@Service
public class DroneServiceImpl extends ServiceImpl<DroneMapper, Drone> implements DroneService {
    @Autowired private CommandExecutor commands;
    @Autowired private EventRecorder events;
    @Autowired private StateWrites stateWrites;
    @Autowired private InventoryService inventory;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;


    @Autowired
    private DroneStockLogMapper stockLogMapper;

    @Override
    public IPage<Drone> pageAvailableDrones(Integer pageNum, Integer pageSize, String keyword, String brand,
                                             String type, Integer status, java.math.BigDecimal minPrice,
                                             java.math.BigDecimal maxPrice, String sortBy, String sortOrder) {
        Page<Drone> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Drone> wrapper = new LambdaQueryWrapper<>();

        // 只查上架的无人机
        wrapper.eq(Drone::getOnShelf, Constants.ON_SHELF_YES);

        // 状态筛选: 1-可租赁(有库存), 0-缺货(无库存)
        if (status != null) {
            if (status == 1) {
                wrapper.gt(Drone::getStock, 0);
                wrapper.eq(Drone::getStatus, Constants.DRONE_STATUS_AVAILABLE);
            } else {
                wrapper.eq(Drone::getStock, 0);
            }
        } else {
            // 默认只查在售状态
            wrapper.eq(Drone::getStatus, Constants.DRONE_STATUS_AVAILABLE);
        }

        // 关键词搜索（型号或品牌）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Drone::getModel, keyword)
                    .or()
                    .like(Drone::getBrand, keyword));
        }
        // 品牌筛选
        if (StringUtils.hasText(brand)) {
            wrapper.eq(Drone::getBrand, brand);
        }
        // 类型筛选
        if (StringUtils.hasText(type)) {
            wrapper.eq(Drone::getType, type);
        }
        // 价格区间
        if (minPrice != null) {
            wrapper.ge(Drone::getPricePerDay, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(Drone::getPricePerDay, maxPrice);
        }

        // 排序
        if ("pricePerDay".equals(sortBy)) {
            if ("asc".equals(sortOrder)) {
                wrapper.orderByAsc(Drone::getPricePerDay);
            } else {
                wrapper.orderByDesc(Drone::getPricePerDay);
            }
        } else if ("rentCount".equals(sortBy)) {
            // 暂时用库存排序代替销量
            if ("asc".equals(sortOrder)) {
                wrapper.orderByAsc(Drone::getStock);
            } else {
                wrapper.orderByDesc(Drone::getStock);
            }
        } else {
            // 默认按创建时间排序
            if ("asc".equals(sortOrder)) {
                wrapper.orderByAsc(Drone::getCreatedTime);
            } else {
                wrapper.orderByDesc(Drone::getCreatedTime);
            }
        }

        return this.page(page, wrapper);
    }

    @Override
    public IPage<Drone> pageAllDrones(Integer pageNum, Integer pageSize, String keyword, String type, Integer status, Integer onShelf) {
        Page<Drone> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Drone> wrapper = new LambdaQueryWrapper<>();

        // 关键词模糊查询（型号或品牌）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Drone::getModel, keyword)
                    .or()
                    .like(Drone::getBrand, keyword));
        }
        // 类型筛选
        if (StringUtils.hasText(type)) {
            wrapper.eq(Drone::getType, type);
        }
        if (status != null) {
            wrapper.eq(Drone::getStatus, status);
        }
        if (onShelf != null) {
            wrapper.eq(Drone::getOnShelf, onShelf);
        }
        wrapper.orderByDesc(Drone::getCreatedTime);

        return this.page(page, wrapper);
    }

    @Override
    public Drone getDroneDetail(Long id) {
        Drone drone = this.getById(id);
        if (drone == null) {
            throw new BusinessException(ResultCode.DRONE_NOT_EXIST);
        }
        return drone;
    }

    @Override
    public void addDrone(DroneDTO dto) {
        commands.execute("ADD_DRONE","NEW",null,java.util.Arrays.asList(dto),()->{ addDroneLocked(dto); return null; });
    }

    private void addDroneLocked(DroneDTO dto) {
        Drone drone = new Drone();
        BeanUtils.copyProperties(dto, drone);

        // 设置默认值
        if (drone.getStatus() == null) {
            drone.setStatus(Constants.DRONE_STATUS_AVAILABLE);
        }
        if (drone.getOnShelf() == null) {
            drone.setOnShelf(Constants.ON_SHELF_YES);
        }

        inventory.validateAdminState(drone);
        this.save(drone);
        CommandContext.required().device=drone.getId();CommandContext.required().resource=drone.getId();
        events.record("DRONE",drone.getId(),"DEVICE_CREATED",null,drone.getStatus());

        // 记录库存日志（入库）
        if (drone.getStock() != null && drone.getStock() > 0) {
            saveStockLog(drone.getId(), Constants.STOCK_CHANGE_IN, drone.getStock(), 0, drone.getStock(), null, "新增无人机入库");
        }
    }

    @Override
    public void updateDrone(Long id, DroneDTO dto) {
        commands.execute("EDIT_DRONE","DRONE",id,java.util.Arrays.asList(id,dto),()->{ updateDroneLocked(id,dto); return null; });
    }

    private void updateDroneLocked(Long id, DroneDTO dto) {
        Drone drone = baseMapper.selectForUpdate(id);
        if (drone == null) {
            throw new BusinessException(ResultCode.DRONE_NOT_EXIST);
        }

        Integer oldStock = drone.getStock();
        BeanUtils.copyProperties(dto, drone);
        drone.setId(id);
        inventory.validateAdminState(drone);
        this.updateById(drone);
        events.record("DRONE",id,"DEVICE_EDITED",null,drone.getStatus());

        // 如果库存变化，记录日志
        if (dto.getStock() != null && !dto.getStock().equals(oldStock)) {
            int change = dto.getStock() - oldStock;
            int changeType = change > 0 ? Constants.STOCK_CHANGE_IN : Constants.STOCK_CHANGE_RENT;
            saveStockLog(id, changeType, change, oldStock, dto.getStock(), null, "管理员调整库存");
        }
    }

    @Override
    public void deleteDrone(Long id) {
        commands.execute("DELETE_DRONE","DRONE",id,java.util.Arrays.asList(id),()->{ deleteDroneLocked(id); return null; });
    }

    private void deleteDroneLocked(Long id) {
        Drone drone = baseMapper.selectForUpdate(id);
        if (drone == null) {
            throw new BusinessException(ResultCode.DRONE_NOT_EXIST);
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM rental_order WHERE drone_id=? AND inventory_held=1 AND deleted=0",Integer.class,id)>0 || inventory.active(id)>0)throw new BusinessException(409,"仍有有效占用，不能删除型号");
        this.removeById(id);events.record("DRONE",id,"DEVICE_DELETED",null,null);
    }

    @Override
    public void updateOnShelf(Long id, Integer onShelf) {
        commands.execute("SET_SHELF","DRONE",id,java.util.Arrays.asList(id,onShelf),()->{ updateOnShelfLocked(id,onShelf); return null; });
    }

    private void updateOnShelfLocked(Long id, Integer onShelf) {
        Drone drone = baseMapper.selectForUpdate(id);
        if (drone == null) {
            throw new BusinessException(ResultCode.DRONE_NOT_EXIST);
        }

        if(onShelf==null || (onShelf!=0 && onShelf!=1))throw new BusinessException(400,"无效上架状态");
        drone.setOnShelf(onShelf);
        this.updateById(drone);events.record("DRONE",id,"SHELF_CHANGED",null,onShelf);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        commands.execute("SET_STATUS","DRONE",id,java.util.Arrays.asList(id,status),()->{ updateStatusLocked(id,status); return null; });
    }

    private void updateStatusLocked(Long id, Integer status) {
        inventory.status(id,status);
    }

    @Override
    public void updateStock(Long id, Integer stock) {
        commands.execute("SET_STOCK","DRONE",id,java.util.Arrays.asList(id,stock),()->{ updateStockLocked(id,stock); return null; });
    }

    private void updateStockLocked(Long id, Integer stock) {
        Drone drone = baseMapper.selectForUpdate(id);
        if (drone == null) {
            throw new BusinessException(ResultCode.DRONE_NOT_EXIST);
        }

        if(stock==null || stock<0)throw new BusinessException(400,"库存不能为负");
        Integer oldStock = drone.getStock();
        drone.setStock(stock);

        // 如果库存变为0，更新状态为缺货
        if (stock == 0 && drone.getStatus() == Constants.DRONE_STATUS_AVAILABLE) {
            drone.setStatus(Constants.DRONE_STATUS_OUT_OF_STOCK);
        }
        // 如果库存从0变为有库存，且状态是缺货，恢复为可租赁
        if (stock > 0 && drone.getStatus() == Constants.DRONE_STATUS_OUT_OF_STOCK) {
            drone.setStatus(Constants.DRONE_STATUS_AVAILABLE);
        }

        this.updateById(drone);

        // 记录库存日志
        if (!stock.equals(oldStock)) {
            int change = stock - oldStock;
            int changeType = change > 0 ? Constants.STOCK_CHANGE_IN : Constants.STOCK_CHANGE_RENT;
            saveStockLog(id, changeType, change, oldStock, stock, null, "管理员调整库存");
        }
    }

    @Override
    public void checkDroneRentable(Long droneId) {
        Drone drone = this.getById(droneId);
        if (drone == null) {
            throw new BusinessException(ResultCode.DRONE_NOT_EXIST);
        }

        // 检查上架状态
        if (drone.getOnShelf() != Constants.ON_SHELF_YES) {
            throw new BusinessException(ResultCode.DRONE_OFF_SHELF);
        }

        // 检查无人机状态
        if (drone.getStatus() != Constants.DRONE_STATUS_AVAILABLE) {
            if (drone.getStatus() == Constants.DRONE_STATUS_MAINTENANCE) {
                throw new BusinessException(ResultCode.DRONE_IN_MAINTENANCE);
            }
            throw new BusinessException(ResultCode.DRONE_NOT_AVAILABLE);
        }

        // 检查库存
        if (drone.getStock() <= 0) {
            throw new BusinessException(ResultCode.DRONE_STOCK_NOT_ENOUGH);
        }
    }

    @Override
    public void decreaseStock(Long droneId, int amount, Long orderId) {
        commands.execute("STOCK_ALLOCATE","ORDER",orderId,java.util.Arrays.asList(droneId,amount,orderId),()->{ decreaseStockLocked(droneId,amount,orderId); return null; });
    }

    private void decreaseStockLocked(Long droneId, int amount, Long orderId) {
        inventory.change(droneId,amount,orderId,true);
    }

    @Override
    public void increaseStock(Long droneId, int amount, Long orderId) {
        commands.execute("STOCK_RELEASE","ORDER",orderId,java.util.Arrays.asList(droneId,amount,orderId),()->{ increaseStockLocked(droneId,amount,orderId); return null; });
    }

    private void increaseStockLocked(Long droneId, int amount, Long orderId) {
        inventory.change(droneId,amount,orderId,false);
    }

    @Override
    public java.util.List<String> getAllBrands() {
        LambdaQueryWrapper<Drone> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Drone::getOnShelf, Constants.ON_SHELF_YES);
        wrapper.select(Drone::getBrand);
        wrapper.groupBy(Drone::getBrand);

        return this.list(wrapper).stream()
                .map(Drone::getBrand)
                .filter(brand -> brand != null && !brand.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public java.util.List<String> getAllTypes() {
        LambdaQueryWrapper<Drone> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Drone::getOnShelf, Constants.ON_SHELF_YES);
        wrapper.select(Drone::getType);
        wrapper.groupBy(Drone::getType);

        return this.list(wrapper).stream()
                .map(Drone::getType)
                .filter(type -> type != null && !type.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 保存库存日志
     */
    private void saveStockLog(Long droneId, Integer changeType, Integer changeAmount,
                              Integer beforeStock, Integer afterStock, Long orderId, String remark) {
        inventory.stockLog(droneId,changeType,changeAmount,beforeStock,afterStock,orderId,"ADJUST",null);
    }
}
