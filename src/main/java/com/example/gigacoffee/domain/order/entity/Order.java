package com.example.gigacoffee.domain.order.entity;

import com.example.gigacoffee.common.entity.BaseEntity;
import com.example.gigacoffee.common.exception.BusinessException;
import com.example.gigacoffee.common.exception.ErrorCode;
import com.example.gigacoffee.domain.enums.OrderStatus;
import com.example.gigacoffee.domain.orderMenu.entity.OrderMenu;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long totalPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderMenu> orderMenus = new ArrayList<>();

    public static Order create(Long userId, Long totalPrice) {
        Order order = new Order();
        order.userId = userId;
        order.totalPrice = totalPrice;
        order.orderStatus = OrderStatus.PENDING;
        return order;
    }

    public void complete() {
        this.orderStatus = OrderStatus.COMPLETED;
    }

    public void cancel() {
        if (this.orderStatus != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.ORDER_NOT_CANCELLABLE);
        }
        this.orderStatus = OrderStatus.CANCELLED;
    }

    public void updateTotalPrice(Long totalPrice) {
        this.totalPrice = totalPrice;
    }
}
