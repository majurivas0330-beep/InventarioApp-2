package com.example.core.di

import android.content.Context
import androidx.room.Room
import com.example.R
import com.example.core.database.AppDatabase
import com.example.feature.product.data.remote.FirestoreProductDataSource
import com.example.feature.product.data.repository.ProductRepositoryImpl
import com.example.feature.product.domain.repository.ProductRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            appContext,
            AppDatabase::class.java,
            "inventory.db"
        ).build()
    }

    val auth: FirebaseAuth by lazy {
        Firebase.auth
    }

    val firestore: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance(
            appContext.getString(R.string.firestore_database_id)
        )
    }

    val remoteDataSource: FirestoreProductDataSource by lazy {
        FirestoreProductDataSource(firestore)
    }

    val productRepository: ProductRepository by lazy {
        ProductRepositoryImpl(
            dao = database.productDao(),
            remote = remoteDataSource,
            auth = auth
        )
    }
}
