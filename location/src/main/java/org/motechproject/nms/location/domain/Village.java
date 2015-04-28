package org.motechproject.nms.location.domain;

import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.mds.annotations.UIDisplayable;
import org.motechproject.mds.domain.MdsEntity;

import javax.validation.constraints.NotNull;

@Entity(tableName = "nms_villages")
public class Village extends MdsEntity {

    @Field
    @UIDisplayable(position = 0)
    @NotNull
    private String name;

    @Field
    @UIDisplayable(position = 1)
    @NotNull
    private Long code;

    @Field
    @UIDisplayable(position = 2)
    @NotNull
    private Taluka taluka;

    public Village() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCode() {
        return code;
    }

    public void setCode(Long code) {
        this.code = code;
    }

    public Taluka getTaluka() {
        return taluka;
    }

    public void setTaluka(Taluka taluka) {
        this.taluka = taluka;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Village village = (Village) o;

        if (name != null ? !name.equals(village.name) : village.name != null) {
            return false;
        }
        return !(code != null ? !code.equals(village.code) : village.code != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (code != null ? code.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Village{" +
                "name='" + name + '\'' +
                ", code=" + code +
                ", taluka=" + taluka +
                '}';
    }
}
