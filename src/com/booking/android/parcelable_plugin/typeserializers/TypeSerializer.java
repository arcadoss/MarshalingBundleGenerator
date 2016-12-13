package com.booking.android.parcelable_plugin.typeserializers;

public interface TypeSerializer {

    String writeValue(SerializableValue field, String parcel, String flags);

    String readValue(SerializableValue field, String parcel);

}
