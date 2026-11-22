package com.example.feature.product.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class FirestoreProductDataSource(
    private val db: FirebaseFirestore
) {
    private fun productsCollection(uid: String) =
        db.collection("users").document(uid).collection("products")

    fun observeProducts(uid: String): Flow<List<ProductDto>> =
        productsCollection(uid)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ProductDto::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun fetchFromServer(uid: String): List<ProductDto> =
        productsCollection(uid)
            .get(Source.SERVER)
            .await()
            .toObjects(
                ProductDto::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )

    suspend fun push(uid: String, dto: ProductDto, isNewOnServer: Boolean) {
        val docRef = productsCollection(uid).document(dto.id)
        if (isNewOnServer) {
            docRef.set(createPayload(dto)).await()
            return
        }

        val updatePayload = mapOf(
            "name" to dto.name,
            "description" to dto.description,
            "priceCop" to dto.priceCop,
            "quantity" to dto.quantity,
            "deleted" to dto.deleted,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updatePayload).await()
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
                docRef.set(createPayload(dto)).await()
            } else {
                throw e
            }
        }
    }

    suspend fun seedInitialProducts(uid: String, initialDtos: List<ProductDto>) {
        if (initialDtos.isEmpty()) return
        val batch = db.batch()
        val col = productsCollection(uid)
        for (dto in initialDtos) {
            batch.set(col.document(dto.id), createPayload(dto))
        }
        batch.commit().await()
    }

    private fun createPayload(dto: ProductDto): Map<String, Any> = mapOf(
        "name" to dto.name,
        "description" to dto.description,
        "priceCop" to dto.priceCop,
        "quantity" to dto.quantity,
        "deleted" to dto.deleted,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
