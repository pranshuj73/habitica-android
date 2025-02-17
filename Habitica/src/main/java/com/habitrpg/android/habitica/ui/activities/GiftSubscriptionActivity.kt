package com.habitrpg.android.habitica.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.navigation.navArgs
import com.android.billingclient.api.SkuDetails
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.components.UserComponent
import com.habitrpg.android.habitica.data.SocialRepository
import com.habitrpg.android.habitica.databinding.ActivityGiftSubscriptionBinding
import com.habitrpg.android.habitica.helpers.AppConfigManager
import com.habitrpg.android.habitica.helpers.PurchaseHandler
import com.habitrpg.android.habitica.helpers.PurchaseTypes
import com.habitrpg.android.habitica.helpers.RxErrorHandler
import com.habitrpg.android.habitica.ui.views.subscriptions.SubscriptionOptionView
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GiftSubscriptionActivity : PurchaseActivity() {

    private lateinit var binding: ActivityGiftSubscriptionBinding

    @Inject
    lateinit var socialRepository: SocialRepository
    @Inject
    lateinit var appConfigManager: AppConfigManager
    @Inject
    lateinit var purchaseHandler: PurchaseHandler

    private var giftedUsername: String? = null
    private var giftedUserID: String? = null

    private var selectedSubscriptionSku: SkuDetails? = null
    private var skus: List<SkuDetails> = emptyList()

    override fun getLayoutResId(): Int {
        return R.layout.activity_gift_subscription
    }

    override fun injectActivity(component: UserComponent?) {
        component?.inject(this)
    }

    override fun getContentView(): View {
        binding = ActivityGiftSubscriptionBinding.inflate(layoutInflater)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTitle(R.string.gift_subscription)
        setSupportActionBar(binding.toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        giftedUserID = intent.getStringExtra("userID")
        giftedUsername = intent.getStringExtra("username")
        if (giftedUserID == null && giftedUsername == null) {
            giftedUserID = navArgs<GiftSubscriptionActivityArgs>().value.userID
            giftedUsername = navArgs<GiftSubscriptionActivityArgs>().value.username
        }

        binding.subscriptionButton.setOnClickListener {
            selectedSubscriptionSku?.let { sku -> purchaseSubscription(sku) }
        }

        compositeSubscription.add(
            socialRepository.getMember(giftedUsername ?: giftedUserID).subscribe(
                {
                    binding.avatarView.setAvatar(it)
                    binding.displayNameTextView.username = it.profile?.name
                    binding.displayNameTextView.tier = it.contributor?.level ?: 0
                    binding.usernameTextView.text = "@${it.username}"
                    giftedUserID = it.id
                    giftedUsername = it.username
                },
                RxErrorHandler.handleEmptyError()
            )
        )

        if (appConfigManager.activePromo()?.identifier == "g1g1") {
            binding.giftSubscriptionContainer.visibility = View.VISIBLE
        } else {
            binding.giftSubscriptionContainer.visibility = View.GONE
        }
    }

    override fun onStart() {
        super.onStart()
        CoroutineScope(Dispatchers.IO).launch {
            val subscriptions = purchaseHandler.getAllGiftSubscriptionProducts()
            skus = subscriptions
            withContext(Dispatchers.Main) {
                for (sku in skus) {
                    updateButtonLabel(sku)
                }
                skus.minByOrNull { it.priceAmountMicros }?.let { selectSubscription(it) }
            }
        }
    }
    private fun updateButtonLabel(sku: SkuDetails) {
        val matchingView = buttonForSku(sku)
        if (matchingView != null) {
            matchingView.setPriceText(sku.price)
            matchingView.sku = sku.sku
            matchingView.setOnPurchaseClickListener { selectSubscription(sku) }
        }
    }

    private fun selectSubscription(sku: SkuDetails) {
        for (thisSku in skus) {
            buttonForSku(thisSku)?.setIsSelected(false)
        }
        this.selectedSubscriptionSku = sku
        val subscriptionOptionButton = buttonForSku(this.selectedSubscriptionSku)
        subscriptionOptionButton?.setIsSelected(true)
        binding.subscriptionButton.isEnabled = true
    }

    private fun buttonForSku(sku: SkuDetails?): SubscriptionOptionView? {
        return buttonForSku(sku?.sku)
    }

    private fun buttonForSku(sku: String?): SubscriptionOptionView? {
        return when (sku) {
            PurchaseTypes.Subscription1MonthNoRenew -> binding.subscription1MonthView
            PurchaseTypes.Subscription3MonthNoRenew -> binding.subscription3MonthView
            PurchaseTypes.Subscription6MonthNoRenew -> binding.subscription6MonthView
            PurchaseTypes.Subscription12MonthNoRenew -> binding.subscription12MonthView
            else -> null
        }
    }

    private fun purchaseSubscription(sku: SkuDetails) {
        giftedUserID?.let { id ->
            if (id.isEmpty()) {
                return
            }
            PurchaseHandler.addGift(sku.sku, id)
            purchaseHandler.purchase(this, sku)
        }
    }
}
