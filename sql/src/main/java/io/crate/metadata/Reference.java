/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.metadata;

import com.google.common.base.Function;
import com.google.common.base.MoreObjects;
import io.crate.analyze.symbol.Symbol;
import io.crate.analyze.symbol.SymbolType;
import io.crate.analyze.symbol.SymbolVisitor;
import io.crate.metadata.table.ColumnPolicy;
import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Comparator;
import java.util.Locale;

public class Reference extends Symbol implements Streamable {

    public static final Comparator<Reference> COMPARE_BY_COLUMN_IDENT = new Comparator<Reference>() {
        @Override
        public int compare(Reference o1, Reference o2) {
            return o1.column.compareTo(o2.column);
        }
    };

    public static final Function<? super Reference, ColumnIdent> TO_COLUMN_IDENT = new Function<Reference, ColumnIdent>() {
        @Nullable
        @Override
        public ColumnIdent apply(@Nullable Reference input) {
            return input == null ? null : input.column;
        }
    };

    public static final Function<? super Reference, String> TO_COLUMN_NAME = new Function<Reference, String>() {
        @Nullable
        @Override
        public String apply(@Nullable Reference input) {
            return input == null ? null : input.column.sqlFqn();
        }
    };

    public ColumnIdent column() {
        return column;
    }

    public TableIdent table() {
        return table;
    }

    public enum IndexType {
        ANALYZED,
        NOT_ANALYZED,
        NO;

        public String toString() {
            return name().toLowerCase(Locale.ENGLISH);
        }
    }

    public static final SymbolFactory<Reference> FACTORY = new SymbolFactory<Reference>() {
        @Override
        public Reference newInstance() {
            return new Reference();
        }
    };

    protected DataType type;
    private ColumnIdent column;
    private TableIdent table;
    private ColumnPolicy columnPolicy = ColumnPolicy.DYNAMIC;
    private RowGranularity granularity;
    private IndexType indexType = IndexType.NOT_ANALYZED;
    private boolean nullable = true;

    public Reference() {

    }

    public Reference(TableIdent table,
                     ColumnIdent column,
                     RowGranularity granularity,
                     DataType type) {
        this(table, column, granularity, type, ColumnPolicy.DYNAMIC, IndexType.NOT_ANALYZED, true);
    }

    public Reference(TableIdent table,
                     ColumnIdent column,
                     RowGranularity granularity,
                     DataType type,
                     ColumnPolicy columnPolicy,
                     IndexType indexType,
                     boolean nullable) {
        this.table = table;
        this.column = column;
        this.type = type;
        this.granularity = granularity;
        this.columnPolicy = columnPolicy;
        this.indexType = indexType;
        this.nullable = nullable;
    }

    /**
     * Returns a cloned Reference with the given ident
     */
    public Reference getRelocated(TableIdent table, ColumnIdent column) {
        return new Reference(table, column, granularity, type, columnPolicy, indexType, nullable);
    }

    @Override
    public SymbolType symbolType() {
        return SymbolType.REFERENCE;
    }

    @Override
    public <C, R> R accept(SymbolVisitor<C, R> visitor, C context) {
        return visitor.visitReference(this, context);
    }

    @Override
    public DataType valueType() {
        return type;
    }

    public RowGranularity granularity() {
        return granularity;
    }

    public ColumnPolicy columnPolicy() {
        return columnPolicy;
    }

    public IndexType indexType() {
        return indexType;
    }

    public boolean isNullable() {
        return nullable;
    }

    @Override
    public String toString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this)
                .add("table", table)
                .add("column", column)
                .add("granularity", granularity)
                .add("type", type);
        if (type.equals(DataTypes.OBJECT)) {
            helper.add("column policy", columnPolicy.name());
        }
        helper.add("index type", indexType.toString());
        helper.add("nullable", nullable);
        return helper.toString();
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        table = TableIdent.fromStream(in);
        column = ColumnIdent.fromStream(in);
        type = DataTypes.fromStream(in);
        granularity = RowGranularity.fromStream(in);

        columnPolicy = ColumnPolicy.values()[in.readVInt()];
        indexType = IndexType.values()[in.readVInt()];
        nullable = in.readBoolean();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        table.writeTo(out);
        column.writeTo(out);
        DataTypes.toStream(type, out);
        RowGranularity.toStream(granularity, out);

        out.writeVInt(columnPolicy.ordinal());
        out.writeVInt(indexType.ordinal());
        out.writeBoolean(nullable);
    }


    public static void toStream(Reference reference, StreamOutput out) throws IOException {
        out.writeVInt(reference.symbolType().ordinal());
        reference.writeTo(out);
    }

    public static <R extends Reference> R fromStream(StreamInput in) throws IOException {
        Symbol symbol = SymbolType.values()[in.readVInt()].newInstance();
        symbol.readFrom(in);
        return (R) symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Reference reference = (Reference) o;

        if (nullable != reference.nullable) return false;
        if (!type.equals(reference.type)) return false;
        if (!column.equals(reference.column)) return false;
        if (!table.equals(reference.table)) return false;
        if (columnPolicy != reference.columnPolicy) return false;
        if (granularity != reference.granularity) return false;
        return indexType == reference.indexType;

    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + column.hashCode();
        result = 31 * result + table.hashCode();
        result = 31 * result + columnPolicy.hashCode();
        result = 31 * result + granularity.hashCode();
        result = 31 * result + indexType.hashCode();
        result = 31 * result + (nullable ? 1 : 0);
        return result;
    }
}