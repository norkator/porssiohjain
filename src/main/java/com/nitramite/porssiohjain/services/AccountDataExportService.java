/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.AccountEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountDataExportService {

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public byte[] exportAccountData(Long accountId) {
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        List<EntityType<?>> entityTypes = entityManager.getMetamodel().getEntities().stream()
                .sorted(Comparator.comparing(EntityType::getName))
                .toList();
        Set<Class<?>> exportableEntityClasses = findExportableEntityClasses(entityTypes);

        Map<Class<?>, List<Object>> entityRows = new LinkedHashMap<>();
        Map<Class<?>, Set<Object>> ownedIds = new LinkedHashMap<>();
        for (EntityType<?> entityType : entityTypes) {
            if (!exportableEntityClasses.contains(entityType.getJavaType())) {
                continue;
            }
            List<Object> rows = entityManager.createQuery(
                            "select e from " + entityType.getName() + " e",
                            Object.class
                    )
                    .getResultList();
            entityRows.put(entityType.getJavaType(), rows);
            ownedIds.put(entityType.getJavaType(), new LinkedHashSet<>());
        }

        boolean changed;
        do {
            changed = false;
            for (Map.Entry<Class<?>, List<Object>> entry : entityRows.entrySet()) {
                Class<?> entityClass = entry.getKey();
                Set<Object> includedIds = ownedIds.get(entityClass);
                for (Object row : entry.getValue()) {
                    Object rowId = persistenceUnitUtil.getIdentifier(row);
                    if (rowId == null || includedIds.contains(rowId)) {
                        continue;
                    }
                    if (belongsToAccount(row, entityClass, accountId, ownedIds, persistenceUnitUtil)) {
                        includedIds.add(rowId);
                        changed = true;
                    }
                }
            }
        } while (changed);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("schemaVersion", 1);
        export.put("generatedAt", Instant.now());
        export.put("accountId", accountId);
        export.put("tables", buildTables(entityRows, ownedIds, persistenceUnitUtil));

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(export)
                    .getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize account data export", ex);
        }
    }

    private boolean belongsToAccount(
            Object row,
            Class<?> entityClass,
            Long accountId,
            Map<Class<?>, Set<Object>> ownedIds,
            PersistenceUnitUtil persistenceUnitUtil
    ) {
        if (AccountEntity.class.equals(entityClass)) {
            return Objects.equals(persistenceUnitUtil.getIdentifier(row), accountId);
        }

        for (Field field : getFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Object value = readField(field, row);
            if (value == null) {
                continue;
            }

            if (AccountEntity.class.isAssignableFrom(field.getType())) {
                return Objects.equals(persistenceUnitUtil.getIdentifier(value), accountId);
            }

            if (isAccountIdField(field) && Objects.equals(value, accountId)) {
                return true;
            }

            if (isEntityReference(field) && isOwnedReference(field.getType(), value, ownedIds, persistenceUnitUtil)) {
                return true;
            }

            if (isForeignKeyToOwnedEntity(field, value, ownedIds)) {
                return true;
            }
        }

        return false;
    }

    private List<Map<String, Object>> buildTables(
            Map<Class<?>, List<Object>> entityRows,
            Map<Class<?>, Set<Object>> ownedIds,
            PersistenceUnitUtil persistenceUnitUtil
    ) {
        List<Map<String, Object>> tables = new ArrayList<>();
        for (Map.Entry<Class<?>, List<Object>> entry : entityRows.entrySet()) {
            Class<?> entityClass = entry.getKey();
            Set<Object> ids = ownedIds.get(entityClass);
            if (ids == null || ids.isEmpty()) {
                continue;
            }

            List<Map<String, Object>> rows = entry.getValue().stream()
                    .filter(row -> ids.contains(persistenceUnitUtil.getIdentifier(row)))
                    .sorted(Comparator.comparing(row -> String.valueOf(persistenceUnitUtil.getIdentifier(row))))
                    .map(row -> serializeEntity(row, entityClass, persistenceUnitUtil))
                    .toList();

            Map<String, Object> table = new LinkedHashMap<>();
            table.put("entity", entityClass.getSimpleName());
            table.put("rowCount", rows.size());
            table.put("rows", rows);
            tables.add(table);
        }
        return tables;
    }

    private Set<Class<?>> findExportableEntityClasses(List<EntityType<?>> entityTypes) {
        Set<Class<?>> entityClasses = new LinkedHashSet<>();
        entityTypes.forEach(entityType -> entityClasses.add(entityType.getJavaType()));

        Set<Class<?>> exportable = new LinkedHashSet<>();
        exportable.add(AccountEntity.class);

        boolean changed;
        do {
            changed = false;
            for (Class<?> entityClass : entityClasses) {
                if (exportable.contains(entityClass)) {
                    continue;
                }
                if (canBelongToExport(entityClass, exportable)) {
                    exportable.add(entityClass);
                    changed = true;
                }
            }
        } while (changed);

        return exportable;
    }

    private boolean canBelongToExport(Class<?> entityClass, Set<Class<?>> exportable) {
        for (Field field : getFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            if (AccountEntity.class.isAssignableFrom(field.getType()) || isAccountIdField(field)) {
                return true;
            }

            if (isEntityReference(field) && exportable.contains(field.getType())) {
                return true;
            }

            if (isForeignKeyToExportableEntity(field, exportable)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> serializeEntity(Object row, Class<?> entityClass, PersistenceUnitUtil persistenceUnitUtil) {
        Map<String, Object> output = new LinkedHashMap<>();
        for (Field field : getFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Object value = readField(field, row);
            output.put(field.getName(), serializeValue(field, value, persistenceUnitUtil));
        }
        return output;
    }

    private Object serializeValue(Field field, Object value, PersistenceUnitUtil persistenceUnitUtil) {
        if (value == null || isSimpleValue(value)) {
            return value;
        }

        if (isEntityReference(field)) {
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("entity", field.getType().getSimpleName());
            reference.put("id", persistenceUnitUtil.getIdentifier(value));
            return reference;
        }

        return String.valueOf(value);
    }

    private Object readField(Field field, Object row) {
        try {
            field.setAccessible(true);
            return field.get(row);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read " + field.getName(), ex);
        }
    }

    private boolean isOwnedReference(
            Class<?> referenceType,
            Object value,
            Map<Class<?>, Set<Object>> ownedIds,
            PersistenceUnitUtil persistenceUnitUtil
    ) {
        Set<Object> ids = ownedIds.get(referenceType);
        return ids != null && ids.contains(persistenceUnitUtil.getIdentifier(value));
    }

    private boolean isForeignKeyToOwnedEntity(Field field, Object value, Map<Class<?>, Set<Object>> ownedIds) {
        if (!(value instanceof Long id) || !field.getName().endsWith("Id")) {
            return false;
        }

        String prefix = field.getName().substring(0, field.getName().length() - 2);
        if (prefix.isBlank() || prefix.toLowerCase().contains("account")) {
            return false;
        }

        String normalizedPrefix = prefix.toLowerCase();
        return ownedIds.entrySet().stream().anyMatch(entry -> {
            String normalizedEntityName = entry.getKey().getSimpleName()
                    .replace("Entity", "")
                    .toLowerCase();
            return normalizedEntityName.equals(normalizedPrefix) && entry.getValue().contains(id);
        });
    }

    private boolean isForeignKeyToExportableEntity(Field field, Set<Class<?>> exportable) {
        if (!Long.class.equals(field.getType()) || !field.getName().endsWith("Id")) {
            return false;
        }

        String prefix = field.getName().substring(0, field.getName().length() - 2);
        if (prefix.isBlank() || prefix.toLowerCase().contains("account")) {
            return false;
        }

        String normalizedPrefix = prefix.toLowerCase();
        return exportable.stream()
                .map(entityClass -> entityClass.getSimpleName().replace("Entity", "").toLowerCase())
                .anyMatch(normalizedPrefix::equals);
    }

    private boolean isAccountIdField(Field field) {
        String fieldName = field.getName().toLowerCase();
        return fieldName.endsWith("accountid") && Long.class.equals(field.getType());
    }

    private boolean isEntityReference(Field field) {
        Class<?> type = field.getType();
        return type.isAnnotationPresent(Entity.class)
                || field.isAnnotationPresent(ManyToOne.class)
                || field.isAnnotationPresent(OneToOne.class);
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof Instant
                || value instanceof LocalDate
                || value instanceof LocalDateTime
                || value instanceof LocalTime
                || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime
                || value instanceof BigDecimal
                || value instanceof BigInteger;
    }

    private List<Field> getFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && !Object.class.equals(current)) {
            fields.addAll(List.of(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }
}
