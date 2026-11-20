package com.example.data.firestore

import com.example.domain.model.Account
import com.example.domain.model.AccountType
import com.example.domain.model.Category
import com.example.domain.model.CategoryType

object DefaultSeedData {

    fun defaultCategories(userId: String): List<Pair<String, Category>> {
        val expenseCategories = listOf(
            Triple("cat_exp_food", "Food & Dining", Pair("restaurant", 0xFFEF4444)),
            Triple("cat_exp_transport", "Transportation", Pair("directions_car", 0xFF3B82F6)),
            Triple("cat_exp_shopping", "Shopping", Pair("shopping_bag", 0xFF8B5CF6)),
            Triple("cat_exp_bills", "Bills & Utilities", Pair("receipt_long", 0xFFF59E0B)),
            Triple("cat_exp_health", "Healthcare", Pair("local_hospital", 0xFFEC4899)),
            Triple("cat_exp_education", "Education", Pair("school", 0xFF06B6D4)),
            Triple("cat_exp_entertainment", "Entertainment", Pair("movie", 0xFFA855F7)),
            Triple("cat_exp_travel", "Travel", Pair("flight", 0xFF0EA5E9)),
            Triple("cat_exp_rent", "Rent", Pair("home", 0xFF6366F1)),
            Triple("cat_exp_personal", "Personal Care", Pair("spa", 0xFFF43F5E)),
            Triple("cat_exp_family", "Family", Pair("family_restroom", 0xFF14B8A6)),
            Triple("cat_exp_other", "Other", Pair("category", 0xFF64748B))
        ).map { (id, name, iconColor) ->
            id to Category(
                id = id,
                userId = userId,
                name = name,
                type = CategoryType.EXPENSE.name,
                iconKey = iconColor.first,
                colorHex = iconColor.second,
                systemDefault = true
            )
        }

        val incomeCategories = listOf(
            Triple("cat_inc_salary", "Salary", Pair("payments", 0xFF16A34A)),
            Triple("cat_inc_business", "Business", Pair("storefront", 0xFF0D9488)),
            Triple("cat_inc_freelance", "Freelance", Pair("laptop_mac", 0xFF2563EB)),
            Triple("cat_inc_investment", "Investment", Pair("trending_up", 0xFF10B981)),
            Triple("cat_inc_interest", "Interest", Pair("account_balance", 0xFF059669)),
            Triple("cat_inc_gift", "Gift", Pair("card_giftcard", 0xFFEC4899)),
            Triple("cat_inc_refund", "Refund", Pair("replay", 0xFF3B82F6)),
            Triple("cat_inc_other", "Other", Pair("attach_money", 0xFF64748B))
        ).map { (id, name, iconColor) ->
            id to Category(
                id = id,
                userId = userId,
                name = name,
                type = CategoryType.INCOME.name,
                iconKey = iconColor.first,
                colorHex = iconColor.second,
                systemDefault = true
            )
        }

        return expenseCategories + incomeCategories
    }

    fun defaultStarterAccounts(userId: String): List<Pair<String, Account>> = listOf(
        "acc_default_cash" to Account(
            id = "acc_default_cash",
            userId = userId,
            name = "Cash Wallet",
            type = AccountType.CASH.name,
            openingBalance = 0.0,
            currentBalance = 0.0,
            iconKey = "wallet",
            colorHex = 0xFF16A34A,
            defaultAccount = true
        ),
        "acc_default_bank" to Account(
            id = "acc_default_bank",
            userId = userId,
            name = "Primary Bank",
            type = AccountType.BANK.name,
            openingBalance = 0.0,
            currentBalance = 0.0,
            iconKey = "account_balance",
            colorHex = 0xFF2563EB,
            defaultAccount = false
        )
    )
}
