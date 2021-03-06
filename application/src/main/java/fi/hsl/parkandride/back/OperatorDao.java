// Copyright © 2015 HSL <https://www.hsl.fi>
// This program is dual-licensed under the EUPL v1.2 and AGPLv3 licenses.

package fi.hsl.parkandride.back;

import com.querydsl.core.Tuple;
import com.querydsl.core.types.MappingProjection;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.SimpleExpression;
import com.querydsl.sql.SQLExpressions;
import com.querydsl.sql.dml.SQLInsertClause;
import com.querydsl.sql.dml.SQLUpdateClause;
import com.querydsl.sql.postgresql.PostgreSQLQuery;
import com.querydsl.sql.postgresql.PostgreSQLQueryFactory;
import fi.hsl.parkandride.back.sql.QOperator;
import fi.hsl.parkandride.core.back.OperatorRepository;
import fi.hsl.parkandride.core.domain.*;
import fi.hsl.parkandride.core.service.TransactionalRead;
import fi.hsl.parkandride.core.service.TransactionalWrite;
import fi.hsl.parkandride.core.service.ValidationException;

import static com.google.common.base.MoreObjects.firstNonNull;
import static fi.hsl.parkandride.core.domain.Sort.Dir.ASC;
import static fi.hsl.parkandride.core.domain.Sort.Dir.DESC;

public class OperatorDao implements OperatorRepository {

    public static final String OPERATOR_ID_SEQ = "operator_id_seq";

    private static final SimpleExpression<Long> nextOperatorId = SQLExpressions.nextval(OPERATOR_ID_SEQ);

    private static final Sort DEFAULT_SORT = new Sort("name.fi", ASC);

    private static QOperator qOperator = QOperator.operator;

    private static MultilingualStringMapping nameMapping = new MultilingualStringMapping(qOperator.nameFi, qOperator.nameSv, qOperator.nameEn);

    private static MappingProjection<Operator> operatorMapping = new MappingProjection<Operator>(Operator.class, qOperator.all()) {
        @Override
        protected Operator map(Tuple row) {
            Long id = row.get(qOperator.id);
            if (id == null) {
                return null;
            }
            Operator operator = new Operator();
            operator.id = id;
            operator.name = nameMapping.map(row);
            return operator;
        }
    };

    private final PostgreSQLQueryFactory queryFactory;

    public OperatorDao(PostgreSQLQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @TransactionalWrite
    @Override
    public long insertOperator(Operator operator) {
        return insertOperator(operator, queryFactory.query().select(nextOperatorId).fetchOne());
    }

    @TransactionalWrite
    public long insertOperator(Operator operator, long operatorId) {
        SQLInsertClause insert = queryFactory.insert(qOperator);
        insert.set(qOperator.id, operatorId);
        nameMapping.populate(operator.name, insert);
        insert.execute();
        return operatorId;
    }

    @TransactionalWrite
    @Override
    public void updateOperator(long operatorId, Operator operator) {
        SQLUpdateClause update = queryFactory.update(qOperator);
        update.where(qOperator.id.eq(operatorId));
        nameMapping.populate(operator.name, update);
        if (update.execute() != 1) {
            notFound(operatorId);
        }
    }

    private void notFound(long operatorId) {
        throw new NotFoundException("Operator by id '%s'", operatorId);
    }

    @Override
    @TransactionalRead
    public Operator getOperator(long operatorId) {
        return queryFactory.from(qOperator).select(operatorMapping).where(qOperator.id.eq(operatorId)).fetchOne();
    }

    @Override
    @TransactionalRead
    public SearchResults<Operator> findOperators(OperatorSearch search) {
        final PostgreSQLQuery<Operator> qry = queryFactory.from(qOperator).select(operatorMapping);
        qry.limit(search.getLimit() + 1);
        qry.offset(search.getOffset());
        orderBy(search.getSort(), qry);
        return SearchResults.of(qry.fetch(), search.getLimit());
    }

    private void orderBy(Sort sort, PostgreSQLQuery qry) {
        sort = firstNonNull(sort, DEFAULT_SORT);
        ComparableExpression<String> sortField;
        switch (firstNonNull(sort.getBy(), DEFAULT_SORT.getBy())) {
            case "name.fi": sortField = qOperator.nameFi.lower(); break;
            case "name.sv": sortField = qOperator.nameSv.lower(); break;
            case "name.en": sortField = qOperator.nameEn.lower(); break;
            default: throw invalidSortBy();
        }
        if (DESC.equals(sort.getDir())) {
            qry.orderBy(sortField.desc(), qOperator.id.desc());
        } else {
            qry.orderBy(sortField.asc(), qOperator.id.asc());
        }
    }

    private ValidationException invalidSortBy() {
        return new ValidationException(new Violation("SortBy", "sort.by", "Expected one of 'name.fi', 'name.sv' or 'name.en'"));
    }

}
