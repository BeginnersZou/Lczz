package com.lczz.product.service;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipientAddressService {
    private final JdbcTemplate jdbc;

    public RecipientAddressService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AddressView> list(AuthenticatedUser actor) {
        requireInstaller(actor);
        return jdbc.query("""
                SELECT id,recipient_name,recipient_phone,recipient_address,is_default
                FROM installer_recipient_address WHERE installer_id=? ORDER BY is_default DESC,id DESC
                """, (rs, row) -> new AddressView(rs.getLong("id"), rs.getString("recipient_name"),
                rs.getString("recipient_phone"), rs.getString("recipient_address"), rs.getBoolean("is_default")),
                actor.userId());
    }

    @Transactional
    public AddressView create(AuthenticatedUser actor, RecipientInfo input, boolean makeDefault) {
        lockOwner(actor);
        RecipientInfo info = RecipientInfo.validated(input);
        if (makeDefault) clearDefault(actor);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO installer_recipient_address(installer_id,recipient_name,recipient_phone,recipient_address,is_default)
                    VALUES (?,?,?,?,?)
                    """, new String[]{"id"});
            statement.setLong(1, actor.userId());
            statement.setString(2, info.recipientName());
            statement.setString(3, info.recipientPhone());
            statement.setString(4, info.recipientAddress());
            statement.setBoolean(5, makeDefault);
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("数据库未返回主键");
        return owned(actor, key.getKey().longValue());
    }

    @Transactional
    public AddressView update(AuthenticatedUser actor, long id, RecipientInfo input, boolean makeDefault) {
        lockOwner(actor);
        owned(actor, id);
        RecipientInfo info = RecipientInfo.validated(input);
        if (makeDefault) clearDefault(actor);
        jdbc.update("""
                UPDATE installer_recipient_address SET recipient_name=?,recipient_phone=?,recipient_address=?,is_default=?
                WHERE id=? AND installer_id=?
                """, info.recipientName(), info.recipientPhone(), info.recipientAddress(), makeDefault, id, actor.userId());
        return owned(actor, id);
    }

    @Transactional
    public AddressView setDefault(AuthenticatedUser actor, long id) {
        lockOwner(actor);
        owned(actor, id);
        clearDefault(actor);
        jdbc.update("UPDATE installer_recipient_address SET is_default=TRUE WHERE id=? AND installer_id=?", id, actor.userId());
        return owned(actor, id);
    }

    @Transactional
    public void delete(AuthenticatedUser actor, long id) {
        lockOwner(actor);
        owned(actor, id);
        jdbc.update("DELETE FROM installer_recipient_address WHERE id=? AND installer_id=?", id, actor.userId());
    }

    private AddressView owned(AuthenticatedUser actor, long id) {
        List<AddressView> rows = jdbc.query("""
                SELECT id,recipient_name,recipient_phone,recipient_address,is_default
                FROM installer_recipient_address WHERE id=? AND installer_id=?
                """, (rs, row) -> new AddressView(rs.getLong("id"), rs.getString("recipient_name"),
                rs.getString("recipient_phone"), rs.getString("recipient_address"), rs.getBoolean("is_default")),
                id, actor.userId());
        if (rows.isEmpty()) throw new BusinessException(404, "RECIPIENT_ADDRESS_NOT_FOUND", "收货地址不存在");
        return rows.getFirst();
    }

    private void clearDefault(AuthenticatedUser actor) {
        jdbc.update("UPDATE installer_recipient_address SET is_default=FALSE WHERE installer_id=? AND is_default=TRUE", actor.userId());
    }

    // Serialize writes for one account, including when its address book is still empty.
    private void lockOwner(AuthenticatedUser actor) {
        requireInstaller(actor);
        jdbc.queryForObject("SELECT id FROM sys_user WHERE id=? FOR UPDATE", Long.class, actor.userId());
    }

    private void requireInstaller(AuthenticatedUser actor) {
        if (actor == null || !actor.hasRole(RoleCode.INSTALLER))
            throw new BusinessException(403, "INSTALLER_ONLY", "仅安装师傅可以管理收货地址");
    }

    public record AddressView(long id, String recipientName, String recipientPhone, String recipientAddress, boolean isDefault) { }
}
